#!/usr/bin/env bash
# Mirror completed GitHub Actions runs (artifacts + logs) for a commit into
# ./github-ci-mirror, so the Gitee Go pipeline can archive them as artifacts.

set -euo pipefail

GITHUB_REPO="${GITHUB_REPO:?set pipeline param GITHUB_REPO=owner/name}"
GITHUB_TOKEN="${GITHUB_TOKEN:?set a secret pipeline param with a GitHub PAT (actions:read)}"
SHA="${GITEE_COMMIT:-$(git rev-parse HEAD)}"
OUT_DIR="github-ci-mirror"
MAX_WAIT_MINUTES="${MAX_WAIT_MINUTES:-90}"
WORKFLOWS="${WORKFLOWS:-android.yml android-test.yml ios.yml ios-test.yml}"

API="https://api.github.com/repos/${GITHUB_REPO}"
AUTH=(-H "Authorization: Bearer ${GITHUB_TOKEN}" -H "Accept: application/vnd.github+json" -H "X-GitHub-Api-Version: 2022-11-28")

command -v unzip >/dev/null || { echo "unzip missing, installing"; yum install -y unzip >/dev/null; }

api() { curl -sfL "${AUTH[@]}" "$@"; }

json_field() { python3 -c "import sys,json;print(json.load(sys.stdin)$1)"; }

# Wait until the latest run of $1 at $SHA is completed; print "id|conclusion|html_url".
wait_for_run() {
    local wf="$1"
    local deadline=$(( $(date +%s) + MAX_WAIT_MINUTES * 60 ))
    while true; do
        local runs count status
        runs=$(api "${API}/actions/workflows/${wf}/runs?head_sha=${SHA}&per_page=1")
        count=$(json_field "['total_count']" <<<"$runs")
        if [ "$count" != "0" ]; then
            status=$(json_field "['workflow_runs'][0]['status']" <<<"$runs")
            if [ "$status" = "completed" ]; then
                local id conclusion url
                id=$(json_field "['workflow_runs'][0]['id']" <<<"$runs")
                conclusion=$(json_field "['workflow_runs'][0]['conclusion']" <<<"$runs")
                url=$(json_field "['workflow_runs'][0]['html_url']" <<<"$runs")
                echo "${id}|${conclusion}|${url}"
                return 0
            fi
            echo "[wait] ${wf} run at ${SHA:0:8} is ${status}"
        else
            echo "[wait] ${wf} has no run at ${SHA:0:8} yet"
        fi
        if [ "$(date +%s)" -ge "$deadline" ]; then
            echo "[fail] timed out waiting for ${wf}" >&2
            return 1
        fi
        sleep 60
    done
}

rm -rf "$OUT_DIR"
mkdir -p "$OUT_DIR"
SUMMARY="$OUT_DIR/SUMMARY.md"
{
    echo "# GitHub CI mirror"
    echo
    echo "- commit: ${SHA}"
    echo "- mirrored at: $(date -u '+%Y-%m-%d %H:%M:%S UTC')"
    echo
    echo "| workflow | conclusion | run |"
    echo "| --- | --- | --- |"
} > "$SUMMARY"

for wf in $WORKFLOWS; do
    result=$(wait_for_run "$wf")
    run_id="${result%%|*}"
    rest="${result#*|}"
    conclusion="${rest%%|*}"
    run_url="${rest##*|}"
    wf_dir="$OUT_DIR/${wf%.yml}"
    mkdir -p "$wf_dir"
    echo "| ${wf} | ${conclusion} | ${run_url} |" >> "$SUMMARY"

    echo "[fetch] ${wf} run ${run_id} (${conclusion})"
    # Full logs (zip of per-job text files). The endpoints 302 to a signed URL;
    # curl drops the auth header on the cross-host redirect, which is fine
    # because the signed URL needs no auth.
    api "${API}/actions/runs/${run_id}/logs" -o "$wf_dir/logs.zip" || \
        echo "[warn] logs unavailable for run ${run_id} (expired?)"

    artifacts=$(api "${API}/actions/runs/${run_id}/artifacts?per_page=100")
    count=$(json_field "['total_count']" <<<"$artifacts")
    for i in $(seq 0 $(( count - 1 ))); do
        [ "$count" = "0" ] && break
        name=$(json_field "['artifacts'][$i]['name']" <<<"$artifacts")
        art_id=$(json_field "['artifacts'][$i]['id']" <<<"$artifacts")
        expired=$(json_field "['artifacts'][$i]['expired']" <<<"$artifacts")
        if [ "$expired" = "True" ]; then
            echo "[warn] artifact ${name} of run ${run_id} expired on GitHub, skipped"
            continue
        fi
        echo "[fetch] artifact ${name}"
        api "${API}/actions/artifacts/${art_id}/zip" -o "$wf_dir/${name}.zip"
        mkdir -p "$wf_dir/${name}"
        unzip -qo "$wf_dir/${name}.zip" -d "$wf_dir/${name}"
        rm -f "$wf_dir/${name}.zip"
    done
done

echo "[done] mirrored into ${OUT_DIR}"
cat "$SUMMARY"
