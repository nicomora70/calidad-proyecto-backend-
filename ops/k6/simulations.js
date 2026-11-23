// Load test of POST /api/v1/simulations (ISO/IEC 25010 §2.2: p95 <= 500 ms with 20 concurrent users).
// Run (stack up, investor already created):
//   docker run --rm --network host -v "$PWD/ops/k6:/scripts" \
//     -e API=http://localhost:8080 -e EMAIL=... -e PASSWORD=... grafana/k6:0.54.0 run /scripts/simulations.js
// The per-user rate limit must allow the test traffic (RATE_LIMIT_USER_PER_MINUTE).
import http from 'k6/http';
import { check, fail, sleep } from 'k6';

const API = __ENV.API || 'http://localhost:8080';

export const options = {
  scenarios: {
    simulations: {
      executor: 'constant-vus',
      vus: Number(__ENV.VUS || 20),
      duration: __ENV.DURATION || '30s',
    },
  },
  thresholds: {
    'http_req_duration{name:create_simulation}': ['p(95)<500'],
    'http_req_failed{name:create_simulation}': ['rate<0.01'],
    checks: ['rate>0.99'],
  },
};

export function setup() {
  const res = http.post(
    `${API}/api/v1/auth/login`,
    JSON.stringify({ email: __ENV.EMAIL, password: __ENV.PASSWORD }),
    { headers: { 'Content-Type': 'application/json' } },
  );
  if (res.status !== 200 || res.json('user.mustChangePassword')) {
    fail(`login failed: ${res.status} ${res.body}`);
  }
  return { token: res.json('accessToken') };
}

export default function (data) {
  const capital = 100000 + Math.floor(Math.random() * 9900000);
  const res = http.post(`${API}/api/v1/simulations`, JSON.stringify({ initialCapital: capital }), {
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${data.token}` },
    tags: { name: 'create_simulation' },
  });
  check(res, {
    'status 201': (r) => r.status === 201,
    'has finalAmount': (r) => r.json('finalAmount') !== undefined,
  });
  sleep(1);
}
