"""
Proxy Validator Module
Tests a single residential proxy exit IP against anti-fraud, geolocation,
reverse DNS, and threat intelligence criteria.
"""

import time
import socket
import re
import requests
from config import (
    PROXY_HOST, PROXY_PORT, PROXY_USER, PROXY_PASS,
    MAX_ACCEPTABLE_FRAUD_SCORE, ALLOWED_COUNTRIES, DISALLOW_DATACENTER,
    MAX_LATENCY_MS, COST_PER_BYTE_USD, TIMEOUT_SECONDS, USER_AGENT
)

class ValidationResult:
    def __init__(self):
        self.ip = None
        self.country = None
        self.country_code = None
        self.region = None
        self.city = None
        self.timezone = None
        self.isp = None
        self.org = None
        self.asn = None
        self.hostname = None
        self.fraud_score = None
        self.risk_level = "Unknown"
        self.is_datacenter = False
        self.is_tor = False
        self.is_vpn = False
        self.latency_ms = 0.0
        self.bytes_transferred = 0
        self.cost_usd = 0.0
        self.is_valid = False
        self.rejection_reason = None

    def to_dict(self):
        return {
            "ip": self.ip,
            "is_valid": self.is_valid,
            "rejection_reason": self.rejection_reason,
            "fraud_score": self.fraud_score,
            "risk_level": self.risk_level,
            "isp": self.isp,
            "asn": self.asn,
            "hostname": self.hostname,
            "location": f"{self.city}, {self.region}, {self.country_code}",
            "timezone": self.timezone,
            "latency_ms": round(self.latency_ms, 1),
            "bytes_transferred": self.bytes_transferred,
            "cost_usd": self.cost_usd,
            "is_datacenter": self.is_datacenter
        }

class ProxyValidator:
    def __init__(self, session_tag=None):
        self.session_tag = session_tag

    def get_proxy_url(self):
        auth_pass = PROXY_PASS
        if self.session_tag:
            auth_pass = f"{PROXY_PASS}_session-{self.session_tag}"
        return f"http://{PROXY_USER}:{auth_pass}@{PROXY_HOST}:{PROXY_PORT}"

    def check_scamalytics(self, ip):
        """Fetch real-time fraud score and threat intel from Scamalytics."""
        intel = {
            "score": None,
            "risk": "Unknown",
            "is_datacenter": False,
            "is_tor": False,
            "is_vpn": False,
            "hostname": None
        }
        try:
            url = f"https://scamalytics.com/ip/{ip}"
            headers = {"User-Agent": USER_AGENT}
            resp = requests.get(url, headers=headers, timeout=TIMEOUT_SECONDS)
            if resp.status_code == 200:
                html = resp.text
                score_m = re.search(r'Fraud Score:\s*([0-9]+)', html)
                if score_m:
                    intel["score"] = int(score_m.group(1))

                risk_m = re.search(r'class="panel_title[^"]*">([^<]+)</div>', html)
                if risk_m:
                    intel["risk"] = risk_m.group(1).strip()

                if re.search(r'Datacenter\s*-->\s*Yes', html, re.IGNORECASE):
                    intel["is_datacenter"] = True
                if re.search(r'Tor Exit Node\s*-->\s*Yes', html, re.IGNORECASE):
                    intel["is_tor"] = True
                if re.search(r'Anonymizing VPN\s*-->\s*Yes', html, re.IGNORECASE):
                    intel["is_vpn"] = True
                
                host_m = re.search(r'Hostname\s*-->\s*([a-zA-Z0-9\.\-_]+)', html)
                if host_m:
                    intel["hostname"] = host_m.group(1).strip()
        except Exception:
            pass
        return intel

    def validate_single_rotation(self):
        result = ValidationResult()
        proxy_url = self.get_proxy_url()
        proxies = {"http": proxy_url, "https": proxy_url}

        session = requests.Session()
        session.trust_env = False

        t0 = time.time()
        try:
            # Query exit IP and network details via residential proxy
            geo_url = "http://ip-api.com/json/?fields=status,message,country,countryCode,region,regionName,city,zip,lat,lon,timezone,isp,org,as,query"
            headers = {"User-Agent": USER_AGENT, "Connection": "close"}
            
            resp = session.get(geo_url, proxies=proxies, headers=headers, timeout=TIMEOUT_SECONDS)
            t1 = time.time()
            result.latency_ms = (t1 - t0) * 1000.0

            # Compute bandwidth used for this check
            down_bytes = len(resp.content) + sum(len(k) + len(v) for k, v in resp.headers.items())
            up_bytes = 450 # Approximate HTTP GET header size
            result.bytes_transferred = down_bytes + up_bytes
            result.cost_usd = result.bytes_transferred * COST_PER_BYTE_USD

            if resp.status_code != 200:
                result.is_valid = False
                result.rejection_reason = f"Proxy HTTP Error {resp.status_code}"
                return result

            data = resp.json()
            if data.get("status") != "success":
                result.is_valid = False
                result.rejection_reason = f"Geo Lookup Failed: {data.get('message', 'Unknown')}"
                return result

            result.ip = data.get("query")
            result.country = data.get("country")
            result.country_code = data.get("countryCode")
            result.region = data.get("regionName")
            result.city = data.get("city")
            result.timezone = data.get("timezone")
            result.isp = data.get("isp")
            result.org = data.get("org")
            result.asn = data.get("as")

            # Local Reverse DNS check
            try:
                result.hostname = socket.gethostbyaddr(result.ip)[0]
            except Exception:
                result.hostname = None

            # Threat Intel & Fraud Scoring
            intel = self.check_scamalytics(result.ip)
            result.fraud_score = intel.get("score")
            result.risk_level = intel.get("risk", "Unknown")
            result.is_datacenter = intel.get("is_datacenter", False)
            result.is_tor = intel.get("is_tor", False)
            result.is_vpn = intel.get("is_vpn", False)
            if not result.hostname and intel.get("hostname"):
                result.hostname = intel["hostname"]

            # Evaluation Against Anti-Detection Thresholds
            if result.country_code not in ALLOWED_COUNTRIES:
                result.is_valid = False
                result.rejection_reason = f"Non-US Country: {result.country_code}"
                return result

            if DISALLOW_DATACENTER and result.is_datacenter:
                result.is_valid = False
                result.rejection_reason = "Flagged Commercial Datacenter"
                return result

            if result.is_tor or result.is_vpn:
                result.is_valid = False
                result.rejection_reason = "Flagged Public Tor/VPN Exit"
                return result

            if result.fraud_score is not None and result.fraud_score > MAX_ACCEPTABLE_FRAUD_SCORE:
                result.is_valid = False
                result.rejection_reason = f"Fraud Score Too High ({result.fraud_score} > {MAX_ACCEPTABLE_FRAUD_SCORE})"
                return result

            if result.latency_ms > MAX_LATENCY_MS:
                result.is_valid = False
                result.rejection_reason = f"Latency Exceeded ({int(result.latency_ms)}ms > {MAX_LATENCY_MS}ms)"
                return result

            # All checks passed
            result.is_valid = True
            result.rejection_reason = None
            return result

        except requests.exceptions.Timeout:
            result.latency_ms = (time.time() - t0) * 1000.0
            result.is_valid = False
            result.rejection_reason = f"Connection Timeout (> {TIMEOUT_SECONDS}s)"
            return result
        except Exception as e:
            result.latency_ms = (time.time() - t0) * 1000.0
            result.is_valid = False
            result.rejection_reason = f"Proxy Socket Error: {str(e)[:45]}"
            return result
        finally:
            session.close()

if __name__ == "__main__":
    validator = ProxyValidator()
    print("Testing single rotation...")
    res = validator.validate_single_rotation()
    import json
    print(json.dumps(res.to_dict(), indent=2))
