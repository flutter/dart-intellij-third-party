import sys
import re

file_path = "fetch_issues.py"
with open(file_path, "r") as f:
    content = f.read()

pattern = re.compile(r"    print\(f\"Fetching assignees from CODEOWNERS for \{args\.repo\}\.\.\.\"\).*?    if raw_assignees:", re.DOTALL)

new_block = """    print(f"Fetching assignees for {args.repo}...")
    # Fetch CODEOWNERS
    import base64
    codeowners_b64 = run_cmd(["gh", "api", f"repos/{args.repo}/contents/.github/CODEOWNERS", "--jq", ".content"])
    
    owners = []
    if codeowners_b64:
        try:
            codeowners_text = base64.b64decode(codeowners_b64).decode('utf-8')
            for line in codeowners_text.splitlines():
                line = line.split('#')[0].strip()
                if line:
                    tokens = line.split()
                    for t in tokens:
                        if t.startswith('@'):
                            handle = t[1:]
                            if '/' not in handle and handle not in owners:
                                owners.append(handle)
        except Exception as e:
            pass
            
    # Fetch all assignees
    assignees_json = run_cmd(["gh", "api", f"repos/{args.repo}/assignees", "--paginate", "--jq", ".[].login"])
    raw_assignees = []
    if assignees_json:
        all_assignees = [a.strip() for a in assignees_json.splitlines() if a.strip()]
        # Put owners first
        raw_assignees = [o for o in owners if o in all_assignees]
        for a in all_assignees:
            if a not in raw_assignees:
                raw_assignees.append(a)

    assignees = []
    if raw_assignees:"""

if pattern.search(content):
    content = pattern.sub(new_block, content)
    with open(file_path, "w") as f:
        f.write(content)
    print("Patched fetch_issues.py successfully.")
else:
    print("Could not find insertion point in fetch_issues.py")
