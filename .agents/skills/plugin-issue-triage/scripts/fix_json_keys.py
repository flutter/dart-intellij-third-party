import json

file_path = "/Users/pquitslund/.gemini/jetski/brain/5960521d-de87-42a5-a239-44099506d0d4/scratch/issues_to_triage_dart.json"
with open(file_path, "r") as f:
    data = json.load(f)

new_assignees = []
for a in data.get("assignees", []):
    new_assignees.append({"login": a.get("handle") or a.get("login"), "name": a.get("name")})

data["assignees"] = new_assignees

with open(file_path, "w") as f:
    json.dump(data, f, indent=2)

print("Fixed JSON assignee keys.")
