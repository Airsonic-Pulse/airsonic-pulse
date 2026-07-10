# Setting up a reverse proxy

Note: This page was inherited from the upstream Airsonic-Advanced project and has not yet been validated with Airsonic-Pulse. The content may still be correct, but some details (versions, ports, paths, screenshots) may be outdated. If you want to suggest a specific correction, feel free to [raise it as an issue](https://github.com/Airsonic-Pulse/airsonic-pulse/issues)


A reverse proxy is a public-facing web server sitting in front of an internal server such as Airsonic Advanced. The Airsonic Advanced server never communicates with the outside ; instead, the reverse proxy handles all HTTP(S) requests and forwards them to Airsonic Advanced.

This is useful in many ways, such as gathering all web configuration in the same place. It also handles some options (HTTPS) much better than the bundled Airsonic Advanced server or a servlet container such as Tomcat.

This guide assumes you already have a working Airsonic Advanced installation after following the [installation guide](https://airsonic.github.io/docs/install/prerequisites/).

## Getting a TLS certificate
This guide assumes you already have a TLS certificate. [Let’s Encrypt](https://letsencrypt.org/getting-started/) currently provides such certificates for free using the [certbot software](https://certbot.eff.org/).

## Configure Airsonic Advanced

### Basic configuration

A few settings should be tweaked via Spring Boot or Tomcat configuration:

- Set the context path if needed (the rest of this guide assumes `/airsonic`, the default value is `/`)
- Set the correct address to listen to (the rest of this guide assumes `127.0.0.1`)
- Set the correct port to listen to (the rest of this guide assumes `4040`)
  - If you see `airsonic.github.io`, its guide assumes `8080` as the default port

To change this, please use one of the guide below according to your installation:

- [Tomcat](https://airsonic.github.io/docs/configure/tomcat/)
- [Standalone](https://airsonic.github.io/docs/configure/standalone/)

### Forward headers

You will also need to make sure Airsonic Advanced uses the correct headers for redirects, by setting the `server.forward-headers-strategy` property to `native` or `framework`.

To do so, stop your Airsonic Advanced server or Docker image, then edit the config/application.properties file:

```
nano /path/to/airsonic/config/application.properties
```

Add the following line to the bottom of the file:

```
server.forward-headers-strategy=native
```

or 

```
server.forward-headers-strategy=framework
```

Use Ctrl+X to save and exit the file, and restart your Airsonic server or Docker image.

## Reverse proxy configuration

### How it works

Airsonic expects proxies to provide information about their incoming URL so that Airsonic can craft it when needed. To do so, Airsonic looks for the following HTTP headers:

- `X-Forwarded-Host`
    - Provides server name and optionally port in the case that the proxy is on a non-standard port
- `X-Forwarded-Proto`
    - Tells Airsonic whether to craft an HTTP or HTTPS url
- `X-Forwarded-Server`
    - This is only a fallback in the case that `X-Forwarded-Host` is not available

Currently this is used wherever, `NetworkUtil#getBaseUrl` is called. A couple notable places include:

- Stream urls
- Share urls
- Coverart urls

## Provided configurations

Use a guide in the list below:

- [Configure Apache proxy](./apache.md)
- [Configure Nginx proxy](https://airsonic.github.io/docs/proxy/nginx/)
- [Configure Haproxy proxy](https://airsonic.github.io/docs/proxy/haproxy)
- [Configure Caddy proxy](https://airsonic.github.io/docs/proxy/caddy)