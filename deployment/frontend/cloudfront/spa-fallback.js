// CloudFront Function (cloudfront-js-2.0), viewer-request on the DEFAULT behavior only.
// Extensionless paths and directory paths are served by the SPA's index.html.
// Paths with a file extension (hashed assets, favicon, config.json) pass through unchanged.
function handler(event) {
  var request = event.request;
  var uri = request.uri;
  if (uri.endsWith('/')) {
    request.uri = '/index.html';
  } else if (!uri.includes('.')) {
    request.uri = '/index.html';
  }
  return request;
}
