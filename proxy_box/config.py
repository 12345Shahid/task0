# Proxy Box Configuration
# Defines credentials, quality criteria, cost parameters, and test thresholds.

PROXY_HOST = "thehub.proxy-cheap.com"
PROXY_PORT = 8080
PROXY_USER = "Ltj8SfJpTEukyVn"
PROXY_PASS = "0Pa7rRMzcehfeLR_country-US"

# Quality & Anti-Fraud Thresholds
MAX_ACCEPTABLE_FRAUD_SCORE = 25       # 0-25 is Low Risk; >25 is Medium/High Risk
ALLOWED_COUNTRIES = ["US", "United States"]
DISALLOW_DATACENTER = True            # Rejects commercial data centers/hosting
MAX_LATENCY_MS = 6000                 # Maximum acceptable response latency (6.0s)

# Economic / Cost Model (Proxy-Cheap Residential)
COST_PER_GB_USD = 3.50                # .50 per GB of residential bandwidth
COST_PER_BYTE_USD = COST_PER_GB_USD / (1024.0 * 1024.0 * 1024.0)

# Default Test Parameters
DEFAULT_SAMPLE_SIZE = 25              # Number of rotation samples per benchmark run
TIMEOUT_SECONDS = 8.0
USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
