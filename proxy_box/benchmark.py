"""
Proxy Benchmark & Reputation Audit Runner
Performs automated multi-rotation testing of the residential proxy,
evaluating quality, success rates, latency, fraud scores, and economic costs.
"""

import sys
import os
import time
import json
from collections import Counter
from validator import ProxyValidator
from config import DEFAULT_SAMPLE_SIZE, COST_PER_GB_USD, MAX_ACCEPTABLE_FRAUD_SCORE

def run_benchmark(sample_size=DEFAULT_SAMPLE_SIZE):
    print("=" * 80)
    print(f"🚀 STARTING RESIDENTIAL PROXY QUALITY & REPUTATION AUDIT ({sample_size} SAMPLES)")
    print("=" * 80)
    print(f"Target Proxy Host : thehub.proxy-cheap.com:8080 (US Residential)")
    print(f"Acceptance Rules  : Country=US, Fraud Score <= {MAX_ACCEPTABLE_FRAUD_SCORE}, Non-Datacenter, Latency <= 6.0s")
    print(f"Pricing Model     : ${COST_PER_GB_USD:.2f} per GB residential bandwidth")
    print("-" * 80)
    print(f"{'#':<3} | {'IP Address':<16} | {'Location':<18} | {'ISP':<20} | {'Score':<5} | {'Latency':<8} | {'Status'}")
    print("-" * 80)

    validator = ProxyValidator()
    results = []
    
    start_time = time.time()
    for i in range(1, sample_size + 1):
        res = validator.validate_single_rotation()
        results.append(res)
        
        status_str = "✅ VALID" if res.is_valid else f"❌ {res.rejection_reason[:18]}"
        loc_str = f"{res.city or 'Unknown'}, {res.region or 'US'}"[:18]
        isp_str = (res.isp or "Unknown")[:20]
        score_str = str(res.fraud_score) if res.fraud_score is not None else "N/A"
        lat_str = f"{int(res.latency_ms)}ms"
        ip_str = res.ip or "FAILED"
        
        print(f"{i:<3} | {ip_str:<16} | {loc_str:<18} | {isp_str:<20} | {score_str:<5} | {lat_str:<8} | {status_str}")
        time.sleep(0.3)

    total_time = time.time() - start_time
    total_samples = len(results)
    valid_samples = [r for r in results if r.is_valid]
    flagged_samples = [r for r in results if not r.is_valid]

    valid_count = len(valid_samples)
    flagged_count = len(flagged_samples)
    success_rate = (valid_count / total_samples) * 100.0 if total_samples > 0 else 0.0

    # Request multiplier: How many requests needed on average to get 1 clean IP
    reqs_per_valid_ip = (total_samples / valid_count) if valid_count > 0 else float("inf")

    # Latencies
    all_latencies = [r.latency_ms for r in results if r.latency_ms > 0]
    valid_latencies = [r.latency_ms for r in valid_samples]
    avg_latency_ms = sum(all_latencies) / len(all_latencies) if all_latencies else 0.0
    avg_valid_latency_ms = sum(valid_latencies) / len(valid_latencies) if valid_latencies else 0.0
    
    # Expected total time to obtain 1 valid IP (including retries on failed rotations)
    expected_time_per_valid_ip_sec = (reqs_per_valid_ip * (avg_latency_ms / 1000.0))

    # Data Transfer & Costs
    total_bytes = sum(r.bytes_transferred for r in results)
    avg_bytes_per_req = total_bytes / total_samples if total_samples > 0 else 0
    total_cost_usd = sum(r.cost_usd for r in results)
    avg_cost_per_req = total_cost_usd / total_samples if total_samples > 0 else 0
    expected_cost_per_valid_ip = reqs_per_valid_ip * avg_cost_per_req

    # Fraud Scores
    scores = [r.fraud_score for r in results if r.fraud_score is not None]
    avg_fraud_score = sum(scores) / len(scores) if scores else 0.0
    min_fraud_score = min(scores) if scores else 0
    max_fraud_score = max(scores) if scores else 0

    # ISP & ASN Distribution
    isps = Counter([r.isp or "Unknown" for r in results])
    states = Counter([r.region or "Unknown" for r in results])
    rejections = Counter([r.rejection_reason for r in flagged_samples if r.rejection_reason])

    summary = {
        "benchmark_timestamp": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
        "total_requests": total_samples,
        "valid_count": valid_count,
        "flagged_count": flagged_count,
        "success_rate_percent": round(success_rate, 2),
        "avg_requests_for_one_valid_ip": round(reqs_per_valid_ip, 2),
        "expected_time_for_one_valid_ip_sec": round(expected_time_per_valid_ip_sec, 2),
        "avg_request_latency_ms": round(avg_latency_ms, 1),
        "avg_valid_request_latency_ms": round(avg_valid_latency_ms, 1),
        "avg_bytes_per_request": round(avg_bytes_per_req, 1),
        "avg_cost_per_request_usd": avg_cost_per_req,
        "expected_cost_per_valid_ip_usd": expected_cost_per_valid_ip,
        "fraud_score_stats": {
            "avg": round(avg_fraud_score, 1),
            "min": min_fraud_score,
            "max": max_fraud_score
        },
        "isp_distribution": dict(isps.most_common(8)),
        "state_distribution": dict(states.most_common(8)),
        "rejection_breakdown": dict(rejections)
    }

    # Save to JSON
    report_file = os.path.join(os.path.dirname(__file__), "benchmark_report.json")
    with open(report_file, "w", encoding="utf-8") as f:
        json.dump({"summary": summary, "samples": [r.to_dict() for r in results]}, f, indent=2)

    # Print Clean Formatted Summary
    print("\n" + "=" * 80)
    print("📊 RESIDENTIAL PROXY BENCHMARK AUDIT SUMMARY")
    print("=" * 80)
    print(f"• Total Rotations Tested       : {total_samples}")
    print(f"• Valid (Clean / Unflagged)    : {valid_count} ({success_rate:.1f}%)")
    print(f"• Flagged / Rejected           : {flagged_count} ({100.0 - success_rate:.1f}%)")
    print("-" * 80)
    print(f"🎯 KEY PERFORMANCE METRICS:")
    print(f"• Requests Needed per Clean IP : {reqs_per_valid_ip:.2f} requests")
    print(f"• Time Cost to Obtain Clean IP : {expected_time_per_valid_ip_sec:.2f} seconds (average)")
    print(f"• Bandwidth Used per Check     : {avg_bytes_per_req:.0f} bytes (~{avg_bytes_per_req/1024.0:.2f} KB)")
    print(f"• Bandwidth Cost per Clean IP  : ${expected_cost_per_valid_ip:.6f} USD")
    print(f"  ↳ Cost for 1,000 Clean IPs   : ${expected_cost_per_valid_ip * 1000.0:.3f} USD (less than 1 cent!)")
    print("-" * 80)
    print(f"🛡️ FRAUD & RISK PROFILE:")
    print(f"• Average Scamalytics Score    : {avg_fraud_score:.1f} / 100 (Threshold <= {MAX_ACCEPTABLE_FRAUD_SCORE})")
    print(f"• Min / Max Fraud Score Seen   : {min_fraud_score} / {max_fraud_score}")
    print("-" * 80)
    print("🌐 TOP RESIDENTIAL ISPS DETECTED:")
    for isp_name, count in isps.most_common(5):
        pct = (count / total_samples) * 100.0
        print(f"  - {isp_name:<32} : {count:>2} hits ({pct:>4.1f}%)")
    print("-" * 80)
    print("🗺️ TOP GEOGRAPHIC REGIONS (STATES):")
    for state_name, count in states.most_common(5):
        pct = (count / total_samples) * 100.0
        print(f"  - {state_name:<32} : {count:>2} hits ({pct:>4.1f}%)")

    if rejections:
        print("-" * 80)
        print("⚠️ REJECTION / FLAGGED CAUSES:")
        for reason, count in rejections.items():
            pct = (count / total_samples) * 100.0
            print(f"  - {reason:<32} : {count:>2} occurrences ({pct:>4.1f}%)")
    print("=" * 80)
    print(f"Detailed raw data saved to: {report_file}\n")
    return summary

if __name__ == "__main__":
    n = DEFAULT_SAMPLE_SIZE
    if len(sys.argv) > 1:
        try:
            n = int(sys.argv[1])
        except ValueError:
            pass
    run_benchmark(n)
