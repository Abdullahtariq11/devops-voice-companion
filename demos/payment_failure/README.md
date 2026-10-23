# Intentional failure for the live demo

This isolated Python example contains a deliberate bug: `calculate_payment_total`
sums `payments[:-1]`, excluding the final payment. A real standard-library unittest
expects 100 for `[60, 30, 10]` but receives 90. It needs no installed dependencies,
GitHub secrets, or AWS credentials. This is not application payment functionality.

The **Failure investigation demo** workflow is manual-only. It does not run on push
or pull requests, is separate from normal CI, and is expected to finish with a red
failed status. Do not add this deliberately failing example to normal test discovery.

## Run the live demo

1. Commit and push the workflow **and both Python files together** to `main`.
   The current investigation inspects the failed run's single head commit, so keeping
   these files in that commit makes the relevant patch and source available.
2. Open GitHub → Actions → **Failure investigation demo** → **Run workflow**.
   Select that commit's branch and start it. Wait for **Run demo tests** to fail.
3. In FailWhisperer, select this repository and ask **“Show me the failed runs.”**
4. Ask **“Summarize the latest failure.”** Then **“Investigate that failure in depth.”**
   If another workflow fails meanwhile, name this demo's run ID explicitly instead.
5. Look for the actual assertion mismatch and evidence that the final payment is
   excluded by `payments[:-1]`. The response must distinguish the intentional demo
   bug from a real application incident and acknowledge investigation limits.

Run locally from this directory to verify the expected nonzero exit:

```bash
python3 -m unittest -v test_payment_service
```

Expected: **one failed test**, expected total **100**, actual total **90**, exit **1**.
Running this workflow uses GitHub Actions runner time; model investigation separately
uses Bedrock. Do not put real credentials or customer data into the demo files.
