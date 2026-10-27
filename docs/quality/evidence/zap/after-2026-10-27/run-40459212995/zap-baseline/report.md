# BlocBuy passive DAST

ZAP by [Checkmarx](https://checkmarx.com/).


## Summary of Alerts

| Risk Level | Number of Alerts |
| --- | --- |
| High | 0 |
| Medium | 0 |
| Low | 0 |
| Informational | 6 |




## Insights

| Level | Reason | Site | Description | Statistic |
| --- | --- | --- | --- | --- |
| Low | Warning |  | ZAP warnings logged - see the zap.log file for details | 2    |
| Info | Informational | https://d1b3np88z657cp.cloudfront.net | Percentage of responses with status code 2xx | 97 % |
| Info | Informational | https://d1b3np88z657cp.cloudfront.net | Percentage of responses with status code 4xx | 2 % |
| Info | Informational | https://d1b3np88z657cp.cloudfront.net | Percentage of endpoints with content type application/json | 9 % |
| Info | Informational | https://d1b3np88z657cp.cloudfront.net | Percentage of endpoints with content type application/manifest+json | 1 % |
| Info | Informational | https://d1b3np88z657cp.cloudfront.net | Percentage of endpoints with content type application/xml | 2 % |
| Info | Informational | https://d1b3np88z657cp.cloudfront.net | Percentage of endpoints with content type image/png | 1 % |
| Info | Informational | https://d1b3np88z657cp.cloudfront.net | Percentage of endpoints with content type image/svg+xml | 1 % |
| Info | Informational | https://d1b3np88z657cp.cloudfront.net | Percentage of endpoints with content type image/vnd.microsoft.icon | 1 % |
| Info | Informational | https://d1b3np88z657cp.cloudfront.net | Percentage of endpoints with content type text/css | 1 % |
| Info | Informational | https://d1b3np88z657cp.cloudfront.net | Percentage of endpoints with content type text/html | 2 % |
| Info | Informational | https://d1b3np88z657cp.cloudfront.net | Percentage of endpoints with content type text/javascript | 80 % |
| Info | Informational | https://d1b3np88z657cp.cloudfront.net | Percentage of endpoints with method GET | 100 % |
| Info | Informational | https://d1b3np88z657cp.cloudfront.net | Count of total endpoints | 87    |
| Info | Informational | https://d1b3np88z657cp.cloudfront.net | Percentage of slow responses | 17 % |







## Alerts

| Name | Risk Level | Number of Instances |
| --- | --- | --- |
| Modern Web Application | Informational | 2 |
| Non-Storable Content | Informational | 2 |
| Re-examine Cache-control Directives | Informational | 3 |
| Retrieved from Cache | Informational | Systemic |
| Storable and Cacheable Content | Informational | Systemic |
| Storable but Non-Cacheable Content | Informational | 2 |




## Alert Detail



### [ Modern Web Application ](https://www.zaproxy.org/docs/alerts/10109/)



##### Informational (Medium)

### Description

The application appears to be a modern web application. If you need to explore it automatically then the Client Spider may well be more effective than the standard one.

* URL: https://d1b3np88z657cp.cloudfront.net/
  * Node Name: `https://d1b3np88z657cp.cloudfront.net/`
  * Method: `GET`
  * Parameter: ``
  * Attack: ``
  * Evidence: `<script type="module" crossorigin src="/assets/index-e1Z7ycW3.js"></script>`
  * Other Info: `No links have been found while there are scripts, which is an indication that this is a modern web application.`
* URL: https://d1b3np88z657cp.cloudfront.net/products/01a11acc-b84a-7134-90bd-a80c8c719739
  * Node Name: `https://d1b3np88z657cp.cloudfront.net/products/01a11acc-b84a-7134-90bd-a80c8c719739`
  * Method: `GET`
  * Parameter: ``
  * Attack: ``
  * Evidence: `<script type="module" crossorigin src="/assets/index-e1Z7ycW3.js"></script>`
  * Other Info: `No links have been found while there are scripts, which is an indication that this is a modern web application.`


Instances: 2

### Solution

This is an informational alert and so no changes are required.

### Reference




#### Source ID: 3

### [ Non-Storable Content ](https://www.zaproxy.org/docs/alerts/10049/)



##### Informational (Medium)

### Description

The response contents are not storable by caching components such as proxy servers. If the response does not contain sensitive, personal or user-specific information, it may benefit from being stored and cached, to improve performance.

* URL: https://d1b3np88z657cp.cloudfront.net/robots.txt
  * Node Name: `https://d1b3np88z657cp.cloudfront.net/robots.txt`
  * Method: `GET`
  * Parameter: ``
  * Attack: ``
  * Evidence: `403`
  * Other Info: ``
* URL: https://d1b3np88z657cp.cloudfront.net/sitemap.xml
  * Node Name: `https://d1b3np88z657cp.cloudfront.net/sitemap.xml`
  * Method: `GET`
  * Parameter: ``
  * Attack: ``
  * Evidence: `403`
  * Other Info: ``


Instances: 2

### Solution

The content may be marked as storable by ensuring that the following conditions are satisfied:
The request method must be understood by the cache and defined as being cacheable ("GET", "HEAD", and "POST" are currently defined as cacheable)
The response status code must be understood by the cache (one of the 1XX, 2XX, 3XX, 4XX, or 5XX response classes are generally understood)
The "no-store" cache directive must not appear in the request or response header fields
For caching by "shared" caches such as "proxy" caches, the "private" response directive must not appear in the response
For caching by "shared" caches such as "proxy" caches, the "Authorization" header field must not appear in the request, unless the response explicitly allows it (using one of the "must-revalidate", "public", or "s-maxage" Cache-Control response directives)
In addition to the conditions above, at least one of the following conditions must also be satisfied by the response:
It must contain an "Expires" header field
It must contain a "max-age" response directive
For "shared" caches such as "proxy" caches, it must contain a "s-maxage" response directive
It must contain a "Cache Control Extension" that allows it to be cached
It must have a status code that is defined as cacheable by default (200, 203, 204, 206, 300, 301, 404, 405, 410, 414, 501).

### Reference


* [ https://datatracker.ietf.org/doc/html/rfc7234 ](https://datatracker.ietf.org/doc/html/rfc7234)
* [ https://datatracker.ietf.org/doc/html/rfc7231 ](https://datatracker.ietf.org/doc/html/rfc7231)
* [ https://www.w3.org/Protocols/rfc2616/rfc2616-sec13.html ](https://www.w3.org/Protocols/rfc2616/rfc2616-sec13.html)


#### CWE Id: [ 524 ](https://cwe.mitre.org/data/definitions/524.html)


#### WASC Id: 13

#### Source ID: 3

### [ Re-examine Cache-control Directives ](https://www.zaproxy.org/docs/alerts/10015/)



##### Informational (Low)

### Description

The cache-control header has not been set properly or is missing, allowing the browser and proxies to cache content. For static assets like css, js, or image files this might be intended, however, the resources should be reviewed to ensure that no sensitive content will be cached.

* URL: https://d1b3np88z657cp.cloudfront.net/
  * Node Name: `https://d1b3np88z657cp.cloudfront.net/`
  * Method: `GET`
  * Parameter: `cache-control`
  * Attack: ``
  * Evidence: `no-cache`
  * Other Info: ``
* URL: https://d1b3np88z657cp.cloudfront.net/manifest.webmanifest
  * Node Name: `https://d1b3np88z657cp.cloudfront.net/manifest.webmanifest`
  * Method: `GET`
  * Parameter: `cache-control`
  * Attack: ``
  * Evidence: `public,max-age=31536000,immutable`
  * Other Info: ``
* URL: https://d1b3np88z657cp.cloudfront.net/products/01a11acc-b84a-7134-90bd-a80c8c719739
  * Node Name: `https://d1b3np88z657cp.cloudfront.net/products/01a11acc-b84a-7134-90bd-a80c8c719739`
  * Method: `GET`
  * Parameter: `cache-control`
  * Attack: ``
  * Evidence: `no-cache`
  * Other Info: ``


Instances: 3

### Solution

For secure content, ensure the cache-control HTTP header is set with "no-cache, no-store, must-revalidate". If an asset should be cached consider setting the directives "public, max-age, immutable".

### Reference


* [ https://cheatsheetseries.owasp.org/cheatsheets/Session_Management_Cheat_Sheet.html#web-content-caching ](https://cheatsheetseries.owasp.org/cheatsheets/Session_Management_Cheat_Sheet.html#web-content-caching)
* [ https://developer.mozilla.org/en-US/docs/Web/HTTP/Reference/Headers/Cache-Control ](https://developer.mozilla.org/en-US/docs/Web/HTTP/Reference/Headers/Cache-Control)
* [ https://grayduck.mn/2021/09/13/cache-control-recommendations/ ](https://grayduck.mn/2021/09/13/cache-control-recommendations/)


#### CWE Id: [ 525 ](https://cwe.mitre.org/data/definitions/525.html)


#### WASC Id: 13

#### Source ID: 3

### [ Retrieved from Cache ](https://www.zaproxy.org/docs/alerts/10050/)



##### Informational (Medium)

### Description

The content was retrieved from a shared cache. If the response data is sensitive, personal or user-specific, this may result in sensitive information being leaked. In some cases, this may even result in a user gaining complete control of the session of another user, depending on the configuration of the caching components in use in their environment. This is primarily an issue where caching servers such as "proxy" caches are configured on the local network. This configuration is typically found in corporate or educational environments, for instance.

* URL: https://d1b3np88z657cp.cloudfront.net/
  * Node Name: `https://d1b3np88z657cp.cloudfront.net/`
  * Method: `GET`
  * Parameter: ``
  * Attack: ``
  * Evidence: `Hit from cloudfront`
  * Other Info: ``
* URL: https://d1b3np88z657cp.cloudfront.net/apple-touch-icon.png
  * Node Name: `https://d1b3np88z657cp.cloudfront.net/apple-touch-icon.png`
  * Method: `GET`
  * Parameter: ``
  * Attack: ``
  * Evidence: `Hit from cloudfront`
  * Other Info: ``
* URL: https://d1b3np88z657cp.cloudfront.net/favicon.ico
  * Node Name: `https://d1b3np88z657cp.cloudfront.net/favicon.ico`
  * Method: `GET`
  * Parameter: ``
  * Attack: ``
  * Evidence: `Hit from cloudfront`
  * Other Info: ``
* URL: https://d1b3np88z657cp.cloudfront.net/favicon.svg
  * Node Name: `https://d1b3np88z657cp.cloudfront.net/favicon.svg`
  * Method: `GET`
  * Parameter: ``
  * Attack: ``
  * Evidence: `Hit from cloudfront`
  * Other Info: ``
* URL: https://d1b3np88z657cp.cloudfront.net/manifest.webmanifest
  * Node Name: `https://d1b3np88z657cp.cloudfront.net/manifest.webmanifest`
  * Method: `GET`
  * Parameter: ``
  * Attack: ``
  * Evidence: `Hit from cloudfront`
  * Other Info: ``

Instances: Systemic


### Solution

Validate that the response does not contain sensitive, personal or user-specific information. If it does, consider the use of the following HTTP response headers, to limit, or prevent the content being stored and retrieved from the cache by another user:
Cache-Control: no-cache, no-store, must-revalidate, private
Pragma: no-cache
Expires: 0
This configuration directs both HTTP 1.0 and HTTP 1.1 compliant caching servers to not store the response, and to not retrieve the response (without validation) from the cache, in response to a similar request.

### Reference


* [ https://datatracker.ietf.org/doc/html/rfc7234 ](https://datatracker.ietf.org/doc/html/rfc7234)
* [ https://datatracker.ietf.org/doc/html/rfc7231 ](https://datatracker.ietf.org/doc/html/rfc7231)
* [ https://www.rfc-editor.org/rfc/rfc9110.html ](https://www.rfc-editor.org/rfc/rfc9110.html)


#### CWE Id: [ 525 ](https://cwe.mitre.org/data/definitions/525.html)


#### Source ID: 3

### [ Storable and Cacheable Content ](https://www.zaproxy.org/docs/alerts/10049/)



##### Informational (Medium)

### Description

The response contents are storable by caching components such as proxy servers, and may be retrieved directly from the cache, rather than from the origin server by the caching servers, in response to similar requests from other users. If the response data is sensitive, personal or user-specific, this may result in sensitive information being leaked. In some cases, this may even result in a user gaining complete control of the session of another user, depending on the configuration of the caching components in use in their environment. This is primarily an issue where "shared" caching servers such as "proxy" caches are configured on the local network. This configuration is typically found in corporate or educational environments, for instance.

* URL: https://d1b3np88z657cp.cloudfront.net/apple-touch-icon.png
  * Node Name: `https://d1b3np88z657cp.cloudfront.net/apple-touch-icon.png`
  * Method: `GET`
  * Parameter: ``
  * Attack: ``
  * Evidence: `max-age=31536000`
  * Other Info: ``
* URL: https://d1b3np88z657cp.cloudfront.net/assets/jsx-runtime-ByaQ0wya.js
  * Node Name: `https://d1b3np88z657cp.cloudfront.net/assets/jsx-runtime-ByaQ0wya.js`
  * Method: `GET`
  * Parameter: ``
  * Attack: ``
  * Evidence: `max-age=31536000`
  * Other Info: ``
* URL: https://d1b3np88z657cp.cloudfront.net/favicon.ico
  * Node Name: `https://d1b3np88z657cp.cloudfront.net/favicon.ico`
  * Method: `GET`
  * Parameter: ``
  * Attack: ``
  * Evidence: `max-age=31536000`
  * Other Info: ``
* URL: https://d1b3np88z657cp.cloudfront.net/favicon.svg
  * Node Name: `https://d1b3np88z657cp.cloudfront.net/favicon.svg`
  * Method: `GET`
  * Parameter: ``
  * Attack: ``
  * Evidence: `max-age=31536000`
  * Other Info: ``
* URL: https://d1b3np88z657cp.cloudfront.net/manifest.webmanifest
  * Node Name: `https://d1b3np88z657cp.cloudfront.net/manifest.webmanifest`
  * Method: `GET`
  * Parameter: ``
  * Attack: ``
  * Evidence: `max-age=31536000`
  * Other Info: ``

Instances: Systemic


### Solution

Validate that the response does not contain sensitive, personal or user-specific information. If it does, consider the use of the following HTTP response headers, to limit, or prevent the content being stored and retrieved from the cache by another user:
Cache-Control: no-cache, no-store, must-revalidate, private
Pragma: no-cache
Expires: 0
This configuration directs both HTTP 1.0 and HTTP 1.1 compliant caching servers to not store the response, and to not retrieve the response (without validation) from the cache, in response to a similar request.

### Reference


* [ https://datatracker.ietf.org/doc/html/rfc7234 ](https://datatracker.ietf.org/doc/html/rfc7234)
* [ https://datatracker.ietf.org/doc/html/rfc7231 ](https://datatracker.ietf.org/doc/html/rfc7231)
* [ https://www.w3.org/Protocols/rfc2616/rfc2616-sec13.html ](https://www.w3.org/Protocols/rfc2616/rfc2616-sec13.html)


#### CWE Id: [ 524 ](https://cwe.mitre.org/data/definitions/524.html)


#### WASC Id: 13

#### Source ID: 3

### [ Storable but Non-Cacheable Content ](https://www.zaproxy.org/docs/alerts/10049/)



##### Informational (Medium)

### Description

The response contents are storable by caching components such as proxy servers, but will not be retrieved directly from the cache, without validating the request upstream, in response to similar requests from other users.

* URL: https://d1b3np88z657cp.cloudfront.net/
  * Node Name: `https://d1b3np88z657cp.cloudfront.net/`
  * Method: `GET`
  * Parameter: ``
  * Attack: ``
  * Evidence: `no-cache`
  * Other Info: ``
* URL: https://d1b3np88z657cp.cloudfront.net/products/01a11acc-b84a-7134-90bd-a80c8c719739
  * Node Name: `https://d1b3np88z657cp.cloudfront.net/products/01a11acc-b84a-7134-90bd-a80c8c719739`
  * Method: `GET`
  * Parameter: ``
  * Attack: ``
  * Evidence: `no-cache`
  * Other Info: ``


Instances: 2

### Solution



### Reference


* [ https://datatracker.ietf.org/doc/html/rfc7234 ](https://datatracker.ietf.org/doc/html/rfc7234)
* [ https://datatracker.ietf.org/doc/html/rfc7231 ](https://datatracker.ietf.org/doc/html/rfc7231)
* [ https://www.w3.org/Protocols/rfc2616/rfc2616-sec13.html ](https://www.w3.org/Protocols/rfc2616/rfc2616-sec13.html)


#### CWE Id: [ 524 ](https://cwe.mitre.org/data/definitions/524.html)


#### WASC Id: 13

#### Source ID: 3


