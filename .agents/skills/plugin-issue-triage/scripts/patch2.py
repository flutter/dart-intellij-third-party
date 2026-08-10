import sys

file_path = "apply_triage.py"
with open(file_path, "r") as f:
    content = f.read()

import re

# We will regex replace the whole # 4. Handle GitHub Project 239 block.
pattern = re.compile(r"            # 4\. Handle GitHub Project 239.*?# 5\. Close Issue if action dictates it", re.DOTALL)

new_block = """            # 4. Handle GitHub Project 239
            add_to_project = dec.get("addToProject", False)
            current_iteration = dec.get("currentIteration", False)
            swarmable = dec.get("swarmable", False)

            if add_to_project:
                print(f"  Adding to DevExp GitHub Project (Flutter 239)...")
                issue_url = run_cmd(["gh", "issue", "view", issue_id, "--json", "url", "--jq", ".url"])
                if issue_url:
                    item_json = run_cmd(["gh", "project", "item-add", "239", "--owner", "flutter", "--url", issue_url, "--format", "json"])
                    if item_json:
                        try:
                            item_data = json.loads(item_json)
                            item_id = item_data.get("id")
                            if item_id and (current_iteration or swarmable):
                                proj_json = run_cmd(["gh", "project", "view", "239", "--owner", "flutter", "--format", "json"])
                                proj_id = json.loads(proj_json).get("id") if proj_json else None
                                
                                if proj_id:
                                    fields_json = run_cmd(["gh", "project", "field-list", "239", "--owner", "flutter", "--format", "json"])
                                    if fields_json:
                                        fields_data = json.loads(fields_json)
                                        fields = fields_data.get("fields", [])
                                        
                                        if current_iteration:
                                            iter_field = next((f for f in fields if f.get("name", "").lower() == "iteration"), None)
                                            if iter_field:
                                                iter_field_id = iter_field["id"]
                                                print(f"  Attempting to set Iteration to Current Iteration...")
                                                query = '''
                                                query {
                                                  node(id: "%s") {
                                                    ... on ProjectV2IterationField {
                                                      configuration {
                                                        iterations {
                                                          id
                                                          title
                                                          startDate
                                                          duration
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
                                                    
                                                    import datetime
                                                    today = datetime.date.today()
                                                    target_iter = None
                                                    for i in iters:
                                                        try:
                                                            s_date = datetime.datetime.strptime(i["startDate"], "%Y-%m-%d").date()
                                                            dur = int(i["duration"])
                                                            e_date = s_date + datetime.timedelta(days=dur)
                                                            if s_date <= today < e_date:
                                                                target_iter = i
                                                                break
                                                        except Exception:
                                                            pass
                                                    
                                                    if target_iter:
                                                        run_cmd(["gh", "project", "item-edit", "--id", item_id, "--project-id", proj_id, "--field-id", iter_field_id, "--iteration-id", target_iter["id"]])
                                                        print(f"  Successfully set Iteration to '{target_iter['title']}'")
                                                    else:
                                                        print(f"  Warning: Could not determine current iteration.")
                                        
                                        if swarmable:
                                            swarm_field = next((f for f in fields if f.get("name", "").lower() == "swarmable"), None)
                                            if swarm_field:
                                                swarm_field_id = swarm_field["id"]
                                                options = swarm_field.get("options", [])
                                                if options:
                                                    target_opt = next((o for o in options if o["name"].lower() in ["yes", "true", "swarmable", "1"]), options[0])
                                                    run_cmd(["gh", "project", "item-edit", "--id", item_id, "--project-id", proj_id, "--field-id", swarm_field_id, "--single-select-option-id", target_opt["id"]])
                                                    print(f"  Successfully set Swarmable")
                                                else:
                                                    run_cmd(["gh", "project", "item-edit", "--id", item_id, "--project-id", proj_id, "--field-id", swarm_field_id, "--text", "Yes"])
                                                    print(f"  Successfully set Swarmable (Text)")
                        except Exception as ex:
                            print(f"  Warning: Failed to add/edit project item: {ex}", file=sys.stderr)

            # 5. Close Issue if action dictates it"""

if pattern.search(content):
    content = pattern.sub(new_block, content)
    with open(file_path, "w") as f:
        f.write(content)
    print("Patched apply_triage.py successfully.")
else:
    print("Could not find insertion point in apply_triage.py")

