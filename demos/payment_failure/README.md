# Intentional failure for the live demo

All demo logic lives in `.github/workflows/failure-demo.yml`: no separate source files,
checkout, or dependencies are required. Its Bash loop deliberately stops before the
last payment. A real shell assertion expects 100 for `[60, 30, 10]` but calculates 90.
This is an intentional workflow-level calculation failure, not a Java/Python test or
application payment functionality. It needs no GitHub secrets or AWS credentials.

The grouped logs include the assertion contract, run/commit context, each input payment,
each processed index and running total, processed versus input count, the expected/actual
totals and their difference. A GitHub error annotation points to the workflow file.
The final failure summary repeats the key facts so bounded log-tail excerpts retain them.
These are diagnostic observations; the assistant should inspect the loop and explain
why the last payment was skipped rather than claim that a real application test failed.

The **Failure investigation demo** workflow is manual-only. It does not run on push
or pull requests, is separate from normal CI, and is expected to finish with a red
failed status.

## Run the live demo

1. Commit and push the updated workflow to `main`.
   The current investigation inspects the failed run's single head commit, so trigger
   it on the commit changing the workflow to make the relevant patch available.
2. Open GitHub → Actions → **Failure investigation demo** → **Run workflow**.
   Select that commit's branch and start it. Wait for **Run demo tests** to fail.
3. In FailWhisperer, select this repository and ask **“Show me the failed runs.”**
4. Ask **“Summarize the latest failure.”** Then **“Investigate that failure in depth.”**
   If another workflow fails meanwhile, name this demo's run ID explicitly instead.
5. Look for the actual assertion mismatch and evidence that the final payment is
   excluded by the loop's `payment_index<${#payments[@]}-1` condition. The response must distinguish the intentional demo
   bug from a real application incident and acknowledge investigation limits.

Expected: **a failed shell assertion**, expected total **100**, actual total **90**, exit **1**.
Running this workflow uses GitHub Actions runner time; model investigation separately
uses Bedrock. Do not put real credentials or customer data into the demo files.
