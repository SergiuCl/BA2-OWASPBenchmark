// Usage:
//   groovy runZapAF.groovy baseline
//   groovy runZapAF.groovy full
//   groovy runZapAF.groovy api
//   groovy runZapAF.groovy baseline https://host.docker.internal:8443/benchmark/

def kind = (args.length > 0 ? args[0] : null)
if (!["baseline","full","api","authenticated-baseline-scan","authenticated-full-scan"].contains(kind)) {
    println "Usage: groovy runZapAF.groovy [baseline|full|api|authenticated-baseline-scan|authenticated-full-scan]"
    System.exit(1)
}

final target = (args.length > 1 ? args[1] : null) ?: System.getenv('TARGET_URL') ?: 'https://host.docker.internal:8443/benchmark/'
final composeFile = 'docker-compose-benchmark-scan.yml'

def waitForApplication(String url, int timeoutSeconds = 60, int intervalSeconds = 2) {
    println "\n============================================"
    println "Checking Application Availability"
    println "============================================"
    println "Target  : ${url}"
    println "Timeout : ${timeoutSeconds}s"
    println "Interval: ${intervalSeconds}s"
    println ""

    def startTime = System.currentTimeMillis()
    def endTime = startTime + (timeoutSeconds * 1000)
    int attempt = 0

    while (System.currentTimeMillis() < endTime) {
        attempt++
        try {
            // curl: -k ignores TLS cert errors, -L follows redirects, -o NUL discards body
            def cmd = ["cmd", "/c", "curl", "-k", "-s", "-L", "-o", "NUL", "-w", "%{http_code}", url]
            def p = new ProcessBuilder(cmd).redirectErrorStream(true).start()
            def out = p.inputStream.text.trim()
            p.waitFor()

            if (out.isInteger() && out.toInteger() in [200, 301, 302, 303, 307, 308]) {
                println "Application is ready! (HTTP ${out})"
                println "============================================\n"
                return true
            } else {
                println "Attempt ${attempt}: HTTP ${out} - retrying..."
            }
        } catch (Exception e) {
            def elapsed = (System.currentTimeMillis() - startTime) / 1000
            println "Attempt ${attempt}: Waiting... (${elapsed.toInteger()}/${timeoutSeconds}s) - ${e.message}"
        }

        Thread.sleep(intervalSeconds * 1000)
    }

    println "\n TIMEOUT: Application did not respond within ${timeoutSeconds} seconds"
    println "============================================\n"
    return false
}

if (!waitForApplication(target)) {
    System.err.println "ERROR: Cannot reach target application at ${target}"
    System.err.println "Please ensure:"
    System.err.println "  1. M@RS application is running"
    System.err.println "  2. URL is correct: ${target}"
    System.err.println "  3. Network connectivity is working"
    System.exit(1)
}

// Setup report directory
def root = new File('reports'); root.mkdirs()
def date = String.format("report-%tF", new Date())
int num = 1
File runDir
while (true) {
    runDir = new File(root, "${date}-${num}")
    if (!runDir.exists()) { runDir.mkdirs(); break }
    num++
}

// Generate ZAP Automation Framework plan
def templatePath = [
        'authenticated-baseline-scan': 'zap-plans/plan.auth-baseline.template.yaml',
        'authenticated-full-scan': 'zap-plans/plan.auth-full.template.yaml',
        'baseline': 'zap-plans/plan.baseline.template.yaml',
        'full'    : 'zap-plans/plan.full.template.yaml',
        'api'     : 'zap-plans/plan.api.template.yaml'
][kind]

// Load and render placeholders
def template = new File(templatePath).getText('UTF-8')
def reportDirInContainer = "/zap/wrk/${root.name}/${runDir.name}"

// Build OpenAPI URL from target URL for API scans
def openapiUrl = "${target}/REST/openapi.json"

def planRendered = template
        .replace('${TARGET_URL}', target)
        .replace('${REPORT_DIR}', reportDirInContainer)
        .replace('${OPENAPI_URL}', openapiUrl)

// Write the generated plan
def genPlan = new File("zap-plans/plan.generated.yaml")
genPlan.setText(planRendered, 'UTF-8')

println """
============================================
Starting OWASP ZAP Scan
============================================
Scan type  : ${kind}
Target     : ${target}
Report dir : ${runDir}
Plan file  : ${genPlan.absolutePath}
============================================
"""

// Run ZAP scan via Docker Compose
def cmd = [
        "docker","compose","-f",composeFile,"--profile","af",
        "run","--rm","-e","PLAN_FILE=/zap/wrk/zap-plans/plan.generated.yaml","zap-af"
]

println "> ${cmd.join(' ')}\n"
def pb = new ProcessBuilder(cmd)
pb.inheritIO()
def p = pb.start()
def code = p.waitFor()

println "\n============================================"
if (code != 0) {
    System.err.println "ZAP scan failed with exit code ${code}"
    System.err.println "============================================"
    System.exit(code)
} else {
    println "ZAP scan completed successfully"
    println "  Reports: ${runDir.absolutePath}"
    println "============================================\n"
}