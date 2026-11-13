import { createServer, type Server } from "node:http";
import {
  activeCheckin,
  closedCheckin,
  completedCheckin,
  dupDocCheckin,
  lastGuestCheckin,
  minorsCheckin,
} from "./fixtures/checkin";
import { citiesByProvince, countries, provinces } from "./fixtures/geo";

export const MOCK_SERVER_PORT = 8090;

const checkinFixtures: Record<string, () => unknown> = {
  ACTIVE: activeCheckin,
  COMPLETED: completedCheckin,
  CLOSED: closedCheckin,
  MINORS: minorsCheckin,
  LASTGUEST: lastGuestCheckin,
  DUPDOC: dupDocCheckin,
};

function json(status: number, body: unknown) {
  return {
    status,
    headers: { "content-type": "application/json" },
    body: JSON.stringify(body),
  };
}

// Response for POST /v1/checkin/:code/guests, keyed by booking code so each
// test can drive a specific submit outcome:
//   LASTGUEST → registration completes the check-in (checkin_completed: true)
//   DUPDOC    → 409 duplicate-document conflict (submit error modal)
//   default   → registration succeeds with guests still pending (success modal)
function handleRegisterGuest(code: string) {
  if (code === "DUPDOC") {
    return json(409, {
      code: "checkin.duplicate_document",
      message: "A guest with this document number has already been registered.",
    });
  }
  const fixture = checkinFixtures[code];
  if (!fixture) {
    return json(404, { code: "checkin.not_found", message: "Booking not found" });
  }
  // Cast for spreading: checkinFixtures is typed `() => unknown`.
  return json(200, {
    ...(fixture() as Record<string, unknown>),
    checkin_completed: code === "LASTGUEST",
  });
}

function handle(method: string, rawUrl: string) {
  const url = new URL(rawUrl, `http://localhost:${MOCK_SERVER_PORT}`);
  const path = url.pathname;

  // --- Geo lookups (browser-side, via the /api/v1 proxy) ---
  if (method === "GET" && path === "/v1/geo/countries") {
    return json(200, { countries });
  }
  if (method === "GET" && path === "/v1/geo/provinces") {
    return json(200, { provinces });
  }
  if (method === "GET" && path === "/v1/geo/cities") {
    const provinceId = url.searchParams.get("province_id") ?? "";
    return json(200, { cities: citiesByProvince[provinceId] ?? [] });
  }

  // --- Guest registration ---
  const registerMatch = path.match(/^\/v1\/checkin\/([^/]+)\/guests$/);
  if (method === "POST" && registerMatch) {
    return handleRegisterGuest(registerMatch[1]);
  }

  // --- Public check-in snapshot (loader) ---
  const checkinMatch = path.match(/^\/v1\/checkin\/([^/]+)$/);
  if (method === "GET" && checkinMatch) {
    const code = checkinMatch[1];
    const fixture = checkinFixtures[code];
    if (fixture) return json(200, fixture());
    return json(404, { code: "checkin.not_found", message: "Booking not found" });
  }

  return json(404, { code: "not_found", message: `No mock for ${method} ${path}` });
}

export function startMockServer(port = MOCK_SERVER_PORT): Promise<Server> {
  const server = createServer((req, res) => {
    // Drain the request body so POST sockets close cleanly even though the
    // mock ignores the payload.
    req.resume();
    const { status, headers, body } = handle(req.method ?? "GET", req.url ?? "/");
    res.writeHead(status, headers);
    res.end(body);
  });
  return new Promise((resolve, reject) => {
    server.once("error", reject);
    server.listen(port, () => resolve(server));
  });
}
