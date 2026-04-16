# CLAUDE.md - Project Context for AI Assistants

## About me and the goals
I am a computer science student in last semester. My bachelor thesis is a comparison of multiple SAST tools: Comparative Evaluation of different Open-Source SAST Tools combined with DAST (OWASP ZAP) in DevSecOps CI/CD Pipelines.
My research questions are:
RQ1:
Which open-source SAST tool provides the highest vulnerability detection accuracy when evaluated using the OWASP Benchmark, both in aggregate and across individual vulnerability categories?
RQ2:
How do the selected SAST tools compare in terms of CI/CD pipeline overhead, measured by total scan duration and pipeline execution time under identical GitHub Actions runner conditions?
RQ3:
How does combining the best-performing SAST tool with OWASP ZAP compare to using each tool individually in terms of vulnerability detection coverage, including findings unique to each tool, overlapping detections, and categories undetected by both?

Methodology:
The thesis follows a multi-phase approach. First, several open-source SAST tools will be selected based on defined criteria, including SARIF output compatibility, CI/CD integration 
capability, and active maintenance. The tools will be installed and tested in a GitHub Actions 
environment using a small test repository containing intentionally introduced vulnerabilities to verify their configuration and basic detection capabilities.
Next, the tools will be integrated into a CI/CD pipeline using GitHub Actions and executed
against the OWASP Benchmark project, which provides a standardized framework for evaluating vulnerability detection capabilities. The evaluation will be based on metrics such as true
positive rate, false positive rate, Youden Index, precision, recall, and F1-score, analyzed both
in aggregate and across individual vulnerability categories. Additionally, 
the pipeline performance of each tool will be measured in terms of scan duration and overall pipeline execution time.
In a separate workflow, OWASP ZAP will be executed against the OWASP Benchmark by deploying it as a running application in a Docker container. This ensures a complete and 
reproducible pipeline where both SAST and DAST are fully integrated.
Finally, the best-performing SAST tool will be selected based on a combination of detection
accuracy and pipeline overhead, and its results will be compared with the standalone ZAP results to analyze how both approaches complement each other in terms of unique findings,
overlapping detections, and vulnerability categories that remain undetected by either tool.

My previous work:
Automating DAST within a CI/CD Pipeline for a Jakarta Faces Web Application
Key achievements: Successfully integrated OWASP ZAP (DAST) into Jenkins CI/CD pipeline
Evaluation: Used OWASP Benchmark to measure detection accuracy
Key Finding: "The best coverage is achieved when DAST is complemented by SAST"

### Your role as AI Assistant
You are my AI assistant specialized in security and writing thesis.
You help me with the pipeline setups and everything I need to finish my thesis.
You are able to analyse research papers that I will ask for.

## OWASP Benchmark Project Overview
**OWASP Benchmark** is a Java test suite designed to evaluate the accuracy, coverage, and speed of automated software vulnerability detection tools (SAST, DAST, IAST). It contains ~2,740 test cases covering 11 vulnerability categories.

### Vulnerability Categories (CWEs)
| Category | CWE | Description |
|----------|-----|-------------|
| cmdi | 78 | Command Injection |
| sqli | 89 | SQL Injection |
| ldapi | 90 | LDAP Injection |
| xpathi | 643 | XPath Injection |
| xss | 79 | Cross-Site Scripting |
| pathtraver | 22 | Path Traversal |
| securecookie | 614 | Insecure Cookie (missing Secure flag) |
| hash | 328 | Weak Hash Algorithm (MD5, SHA1) |
| crypto | 327 | Weak Encryption (DES, DESede) |
| weakrand | 330 | Weak Random Number Generator |
| trustbound | 501 | Trust Boundary Violation |

## Project Structure

```
BenchmarkJava/
├── src/main/java/org/owasp/benchmark/
│   ├── testcode/          # ~2,740 BenchmarkTestXXXXX.java files
│   └── helpers/           # Helper classes used by test cases
├── results/               # SARIF output from security scanners
├── scorecard/             # Benchmark scoring results (CSV, HTML, PNG)
├── .github/workflows/     # CI/CD workflows for security scanners
│   ├── semgrep.yaml       # Semgrep Pro workflow
│   ├── codeql-analysis.yml
│   └── maven.yaml
├── semgrep-config.yaml    # Custom Semgrep rules with sanitizers
├── .semgrepignore         # Files to exclude from Semgrep scanning
└── expectedresults-*.csv  # Ground truth for each Benchmark version
```

## Key Helper Classes

### Safe Sources (cause false positives)
- `SeparateClassRequest.getTheValue(String)` - **Always returns `"bar"`** (constant)
  - Located at: `src/main/java/org/owasp/benchmark/helpers/SeparateClassRequest.java:52-54`
  - Scanners that don't recognize this as safe will report false positives

### Unsafe Sources (tainted data)
- `SeparateClassRequest.getTheParameter(String)` - Wraps `request.getParameter()`
- `SeparateClassRequest.getTheCookie(String)` - Wraps cookie retrieval

### False Positive Patterns in Test Cases
1. **Safe source**: Uses `getTheValue()` which returns constant
2. **Always-true conditions**: Math like `(7*42)-86 > 200` always true, making tainted path unreachable
3. **List index tricks**: ArrayList operations that always retrieve safe values

## Scorecard CSV Format

```
# test name, category, CWE, real vulnerability, identified by tool, pass/fail
BenchmarkTest00001, pathtraver, 22, true, true, pass    # True Positive
BenchmarkTest00051, cmdi, 78, false, true, fail         # False Positive
BenchmarkTest00003, hash, 328, true, false, fail        # False Negative
```

- **real vulnerability = true, identified = true** → True Positive (good)
- **real vulnerability = false, identified = true** → False Positive (bad)
- **real vulnerability = true, identified = false** → False Negative (bad)
- **real vulnerability = false, identified = false** → True Negative (good)

## Current Work: Semgrep Workflow Optimization

### Goal
Reduce false positive rate while maintaining true positive detection.

### Changes Made
1. **Created `semgrep-xss-rules.yaml`** - Custom rules to detect XSS via:
   - `PrintWriter.format()` - not detected by default Semgrep
   - `PrintWriter.printf()` - not detected by default Semgrep
   - Expected to improve XSS TPR from 22% toward 90%+

2. **Updated `.github/workflows/semgrep.yaml`**:
   - Added `--config semgrep-xss-rules.yaml` to include custom XSS rules
   - Kept standard Semgrep CI with Pro rules via SEMGREP_APP_TOKEN

3. **Created `.semgrepignore`** to exclude:
   - `scorecard/`, `results/` directories
   - Build artifacts (`target/`)
   - Non-Java files

4. **Created `semgrep-config.yaml`** (not currently used) with custom rules including sanitizers for:
   - `SeparateClassRequest.getTheValue()` as safe source
   - ESAPI encoding functions

### Current Semgrep Results (v1.153.1)
| Metric | Value |
|--------|-------|
| True Positives | 1,144 |
| False Negatives | 271 |
| False Positives | 476 |
| True Negatives | 849 |
| **TPR** | 80.8% |
| **FPR** | 35.9% |

### Per-Category Analysis
| Category | TPR | FPR | Issue |
|----------|-----|-----|-------|
| cmdi | 92.8% | **88.8%** | Extreme FPR |
| ldapi | 100% | **87.5%** | Extreme FPR |
| xpathi | 100% | **90.0%** | Extreme FPR |
| trustbound | 100% | **79.0%** | Very high FPR |
| pathtraver | 96.2% | **74.0%** | Very high FPR |
| sqli | 90.4% | **65.9%** | High FPR |
| xss | **22.3%** | 15.3% | Very low TPR! |
| hash | 68.9% | 0% | Missing some TPs |
| securecookie | 100% | 0% | Perfect |
| crypto | 100% | 0% | Perfect |
| weakrand | 100% | 0% | Perfect |

### Root Cause Analysis

**XSS False Negatives (77.7% missed):**
Semgrep doesn't recognize these sinks:
- `response.getWriter().format(Locale, String, Object[])`
- `response.getWriter().printf(String, Object[])`

Only `print()` and `println()` are detected as XSS sinks.

**Taint-based False Positives (65-90% FPR):**
1. **Safe source `getTheValue()`** - always returns `"bar"` (constant)
   - Example: `BenchmarkTest00051.java`
2. **Always-true conditions** - e.g., `(7*42)-86 > 200` = 208 > 200 = TRUE
   - Example: `BenchmarkTest00090.java`, `BenchmarkTest00158.java`
3. **Map retrieval of safe keys** - puts tainted in keyB, retrieves from keyA
   - Example: `BenchmarkTest00171.java` lines 54-57
4. **List index tricks** - removes/gets indices that skip tainted value
   - Example: `BenchmarkTest00093.java`

### Limitations
Semgrep lacks:
- **Constant propagation** - can't evaluate `(7*42)-86 > 200`
- **Strong update analysis** - doesn't track variable overwrites
- **Collection-sensitive analysis** - can't track which map key/list index is retrieved

## Commands

### Build
```bash
mvn clean compile
```

### Run Semgrep locally
```bash
# With Pro rules (requires SEMGREP_APP_TOKEN)
semgrep ci --sarif > results/semgrep.sarif

# With custom config only
semgrep scan --config semgrep-config.yaml src/main/java/
```

### Generate Scorecard
```bash
mvn validate -Pscorecard -DskipTests
```

## Git Branch
Current branch: `semgrep-workflow`
Main branch: `main`
