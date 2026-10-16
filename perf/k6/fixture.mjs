// Disposable generator contract fixture. Never packaged into BlocBuy or exposed on a host port.
import http from 'node:http';

const id = '00000000-0000-7000-8000-000000000001';
const mode = process.env.FIXTURE_MODE || 'good';
const counts = { list: 0, detail: 0, other: 0 };
http.createServer((request, response) => {
  response.setHeader('Content-Type', 'application/json');
  if (request.url === '/stats') return response.end(JSON.stringify(counts));
  const detail = request.url === `/api/v1/products/${id}`;
  const list = request.url === '/api/v1/products?page=1&size=20';
  if (!list && !detail) {
    counts.other++;
    response.writeHead(404);
    return response.end('{}');
  }
  if (detail) counts.detail++; else counts.list++;
  if (mode === 'bad-setup' || (mode === 'bad-runtime' && (detail || counts.list > 1))) {
    // A 200 with an error envelope must fail despite its superficially successful status.
    return response.end(JSON.stringify({ code: 'FIXTURE_ERROR', data: null }));
  }
  response.end(JSON.stringify({ code: 'COMMON_200_000', data: detail ? { id } : { items: [{ id }] } }));
}).listen(8080, '0.0.0.0');
