import json

file_path = "/Users/pquitslund/.gemini/jetski/brain/5960521d-de87-42a5-a239-44099506d0d4/scratch/raw_issues_dart.json"
with open(file_path, "r") as f:
    data = json.load(f)

data["assignees"] = [
    {"handle": "helin24", "name": "helin24"},
    {"handle": "pq", "name": "pq"}
]

with open(file_path, "w") as f:
    json.dump(data, f, indent=2)

print("Updated raw_issues_dart.json assignees list.")
