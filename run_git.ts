import { execSync } from "child_process";

try {
  console.log("=== Git Status ===");
  try {
    console.log(execSync("git status", { encoding: "utf8" }));
  } catch (e: any) {
    console.log(e.message);
  }

  console.log("=== Last 10 commits ===");
  try {
    console.log(execSync("git log -n 10 --oneline", { encoding: "utf8" }));
  } catch (e: any) {
    console.log(e.message);
  }

} catch (err: any) {
  console.error("Error executing git command:", err.message);
}
