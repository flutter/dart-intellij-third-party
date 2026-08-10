import json

file_path = "/Users/pquitslund/.gemini/jetski/brain/5960521d-de87-42a5-a239-44099506d0d4/scratch/issues_to_triage_dart.json"
with open(file_path, "r") as f:
    data = json.load(f)

for issue in data.get("issues", []):
    if "suggestions" in issue:
        issue["suggestions"]["assignee"] = ""

with open(file_path, "w") as f:
    json.dump(data, f, indent=2)

print("Cleared assignee suggestions.")
