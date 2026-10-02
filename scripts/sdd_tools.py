import sys
import os
import re
import subprocess

WORKSPACE = os.path.abspath(".superpowers/sdd/2026-10-02-productivity-agent-core-plan")
PLAN_FILE = os.path.abspath("docs/superpowers/plans/2026-10-02-productivity-agent-core-plan.md")

def extract_brief(task_num: int):
    os.makedirs(WORKSPACE, exist_ok=True)
    out_file = os.path.join(WORKSPACE, f"task-{task_num}-brief.md")
    
    with open(PLAN_FILE, "r", encoding="utf-8") as f:
        content = f.read()

    # Find heading matching Task <task_num>:
    pattern = rf"(### Task {task_num}:.*?)(?=\n### Task \d+:|\Z)"
    match = re.search(pattern, content, re.DOTALL)
    if not match:
        print(f"Error: Task {task_num} not found in {PLAN_FILE}", file=sys.stderr)
        sys.exit(1)
        
    with open(out_file, "w", encoding="utf-8") as out:
        out.write(match.group(1).strip() + "\n")
    print(out_file)

def review_package(base: str, head: str):
    os.makedirs(WORKSPACE, exist_ok=True)
    short_base = subprocess.check_output(["git", "rev-parse", "--short", base]).decode().strip()
    short_head = subprocess.check_output(["git", "rev-parse", "--short", head]).decode().strip()
    out_file = os.path.join(WORKSPACE, f"review-{short_base}..{short_head}.diff")

    log_output = subprocess.check_output(["git", "log", "--oneline", f"{base}..{head}"]).decode()
    stat_output = subprocess.check_output(["git", "diff", "--stat", f"{base}..{head}"]).decode()
    diff_output = subprocess.check_output(["git", "diff", "-U10", f"{base}..{head}"]).decode()

    with open(out_file, "w", encoding="utf-8") as f:
        f.write(f"# Review package: {base}..{head}\n\n")
        f.write("## Commits\n")
        f.write(log_output + "\n\n")
        f.write("## Files changed\n")
        f.write(stat_output + "\n\n")
        f.write("## Diff\n")
        f.write(diff_output + "\n")
    print(out_file)

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage: python sdd_tools.py brief <task_num> | package <base> <head>")
        sys.exit(1)
    cmd = sys.argv[1]
    if cmd == "brief":
        extract_brief(int(sys.argv[2]))
    elif cmd == "package":
        review_package(sys.argv[2], sys.argv[3])
