import sys
import re

file_path = "apply_triage.py"
with open(file_path, "r") as f:
    content = f.read()

# We want to insert the project logic before step 4 (closing the issue).
search_str = "# 4. Close Issue if action dictates it"
insert_str = """
            # 4. Handle GitHub Project 239
            add_to_project = dec.get("addToProject", False)
            iteration = dec.get("iteration", "").strip()
            swarmable = dec.get("swarmable", False)

            if add_to_project:
                print(f"  Adding to Flutter Project 239...")
                issue_url = run_cmd(["gh", "issue", "view", issue_id, "--json", "url", "--jq", ".url"])
                if issue_url:
                    item_json = run_cmd(["gh", "project", "item-add", "239", "--owner", "flutter", "--url", issue_url, "--format", "json"])
                    if item_json:
                        try:
                            item_data = json.loads(item_json)
                            item_id = item_data.get("id")
                            if item_id and (iteration or swarmable):
                                proj_json = run_cmd(["gh", "project", "view", "239", "--owner", "flutter", "--format", "json"])
                                proj_id = json.loads(proj_json).get("id") if proj_json else None
                                
                                if proj_id:
                                    fields_json = run_cmd(["gh", "project", "field-list", "239", "--owner", "flutter", "--format", "json"])
                                    if fields_json:
                                        fields_data = json.loads(fields_json)
                                        fields = fields_data.get("fields", [])
                                        
                                        if iteration:
                                            iter_field = next((f for f in fields if f.get("name", "").lower() == "iteration"), None)
                                            if iter_field:
                                                iter_field_id = iter_field["id"]
                                                # Try to find iteration id by title (gh might list it in configuration? or we might need graphql)
                                                # As a fallback or if supported, gh project item-edit might not accept iteration name.
                                                # Actually, we can use the gh project item-edit --iteration-id
                                                # But we don't know iteration-id easily. Let's try text first, or find iteration-id in configuration
                                                print(f"  Attempting to set Iteration: {iteration}")
                                                # We'll need to parse configuration for iterations
                                                # Since field-list output structure for iterations is complex, we'll try a simpler graphql approach if needed,
                                                # but for now let's attempt to use gh api graphql to find the iteration node id if possible.
                                                # To keep it simple, we use the `gh` command and assume the user's string is correct.
                                                # If we can't find it, we'll print a warning.
                                                pass # Complex: needs GraphQL
                                                
                                                # To do it properly:
                                                query = '''
                                                query {
                                                  node(id: "%s") {
                                                    ... on ProjectV2FieldConfiguration {
                                                      ... on ProjectV2IterationField {
                                                        configuration {
                                                          iterations {
                                                            id
                                                            title
                                                          }
                                                        }
                                                      }
                                                    }
                                                  }
                                                }
                                                ''' % iter_field_id
                                                iter_res = run_cmd(["gh", "api", "graphql", "-f", f"query={query}"])
                                                if iter_res:
                                                    iter_data = json.loads(iter_res)
                                                    iters = iter_data.get("data", {}).get("node", {}).get("configuration", {}).get("iterations", [])
                                                    target_iter = next((i for i in iters if i["title"].lower() == iteration.lower()), None)
                                                    if target_iter:
                                                        run_cmd(["gh", "project", "item-edit", "--id", item_id, "--project-id", proj_id, "--field-id", iter_field_id, "--iteration-id", target_iter["id"]])
                                                        print(f"  Successfully set Iteration to '{iteration}'")
                                                    else:
                                                        print(f"  Warning: Iteration '{iteration}' not found in project.")
                                        
                                        if swarmable:
                                            swarm_field = next((f for f in fields if f.get("name", "").lower() == "swarmable"), None)
                                            if swarm_field:
                                                swarm_field_id = swarm_field["id"]
                                                # Assume single-select with an option like "Yes", "True", or just selecting it
                                                options = swarm_field.get("options", [])
                                                if options:
                                                    # Find truthy option
                                                    target_opt = next((o for o in options if o["name"].lower() in ["yes", "true", "swarmable", "1"]), options[0])
                                                    run_cmd(["gh", "project", "item-edit", "--id", item_id, "--project-id", proj_id, "--field-id", swarm_field_id, "--single-select-option-id", target_opt["id"]])
                                                    print(f"  Successfully set Swarmable")
                                                else:
                                                    # If it's a text field
                                                    run_cmd(["gh", "project", "item-edit", "--id", item_id, "--project-id", proj_id, "--field-id", swarm_field_id, "--text", "Yes"])
                                                    print(f"  Successfully set Swarmable (Text)")
                        except Exception as ex:
                            print(f"  Warning: Failed to add/edit project item: {ex}", file=sys.stderr)

            # 5. Close Issue if action dictates it
"""

if search_str in content:
    content = content.replace(search_str, insert_str)
    with open(file_path, "w") as f:
        f.write(content)
    print("Patched apply_triage.py successfully.")
else:
    print("Could not find insertion point in apply_triage.py")
