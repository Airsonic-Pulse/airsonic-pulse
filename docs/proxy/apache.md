# Setting up Apache

This configures Apache as an HTTPS reverse proxy in front of Airsonic-Pulse, with an HTTP-to-HTTPS redirect. The virtual host and proxy configuration is the same on every Linux distribution; only the config file location, how you enable modules, and the service name differ between Debian-family and RHEL-family systems. Those differences are called out in [Enable the site and modules](#enable-the-site-and-modules) below.

> NOTE: Make sure you follow the [prerequisites](./README.md).

## Airsonic-Pulse configuration

Make sure Airsonic-Pulse sends the correct headers for redirects by setting `server.forward-headers-strategy` to `framework` or `native`.

`framework` is the recommended value, and it's what the shipped systemd unit and installers use. Set it to `native` only if you specifically want Apache to supply the forwarded headers.

If you do use `native`, you may also need to set `X-Forwarded-Host` and/or `X-Forwarded-Port`, as described in the [prerequisites](./README.md).

## Virtual host configuration

This block is identical on all distributions. Create a virtual host configuration containing:

```apache
<VirtualHost *:80>
    ServerName example.com
    Redirect permanent / https://example.com/
</VirtualHost>

<VirtualHost *:443>
    ServerName example.com

    SSLEngine On
    SSLCertificateFile cert.pem
    SSLCertificateKeyFile key.pem
    SSLProxyEngine on

    LogLevel warn

    ProxyPass         /airsonic/websocket ws://127.0.0.1:4040/airsonic/websocket
    ProxyPassReverse  /airsonic/websocket ws://127.0.0.1:4040/airsonic/websocket
    ProxyPass         /airsonic http://127.0.0.1:4040/airsonic
    ProxyPassReverse  /airsonic http://127.0.0.1:4040/airsonic
    RequestHeader     set       X-Forwarded-Proto "https"
</VirtualHost>
```

Alternatively, to add the proxy to an existing `VirtualHost` block, paste just these lines inside it:

```apache
ProxyPass         /airsonic/websocket ws://127.0.0.1:4040/airsonic/websocket
ProxyPassReverse  /airsonic/websocket ws://127.0.0.1:4040/airsonic/websocket
ProxyPass         /airsonic http://127.0.0.1:4040/airsonic
ProxyPassReverse  /airsonic http://127.0.0.1:4040/airsonic
RequestHeader     set       X-Forwarded-Proto "https"
```

Then make these changes:

- Replace `example.com` with your own domain name.
- Set the correct paths to your `cert.pem` and `key.pem` files.
- Change `/airsonic` to match your Airsonic-Pulse context path.
- Change `http://127.0.0.1:4040/airsonic` to match your Airsonic-Pulse server address, port, and path.

## Enable the site and modules

This is the part that differs by distribution. Follow the section for your system.

### Debian / Ubuntu

Save the virtual host as `/etc/apache2/sites-available/airsonic.conf`:

```
sudo nano /etc/apache2/sites-available/airsonic.conf
```

Enable the site:

```
sudo a2ensite airsonic.conf
```

Enable the required modules:

```
sudo a2enmod proxy proxy_http proxy_wstunnel ssl headers
```

Restart Apache:

```
sudo systemctl restart apache2
```

### RHEL / CentOS / Fedora / Rocky / Alma

> The commands in this section have not yet been validated on a live RHEL-family system. The mechanics are standard, but on a default httpd install several of these modules may already be loaded by the base configuration, in which case the explicit `LoadModule` lines below are unnecessary or may conflict. Verify against your system.

Save the virtual host as a `.conf` file under `/etc/httpd/conf.d/`, which Apache includes automatically (there is no `a2ensite` step):

```
sudo nano /etc/httpd/conf.d/airsonic.conf
```

There is no `a2enmod` on RHEL. Modules are enabled with `LoadModule` directives. Most are loaded by the base config; if any are missing, add them under `/etc/httpd/conf.modules.d/`. The modules this setup needs are:

```apache
LoadModule proxy_module         modules/mod_proxy.so
LoadModule proxy_http_module    modules/mod_proxy_http.so
LoadModule proxy_wstunnel_module modules/mod_proxy_wstunnel.so
LoadModule ssl_module           modules/mod_ssl.so
LoadModule headers_module       modules/mod_headers.so
```

Check the configuration and restart Apache (the service is `httpd`, not `apache2`):

```
sudo apachectl configtest
sudo systemctl restart httpd
```

If SELinux is enforcing, it blocks Apache from making outbound connections by default, so the proxy to `127.0.0.1:4040` will fail until you allow it:

```
sudo setsebool -P httpd_can_network_connect 1
```

## Content Security Policy

You may hit `Content-Security-Policy` issues. To resolve them, add this to your Apache configuration (inside the `:443` VirtualHost, on any distribution):

```apache
<Location /airsonic>
    Header set Content-Security-Policy "default-src 'self'; script-src 'self' 'unsafe-inline' 'unsafe-eval' www.gstatic.com; img-src 'self' *.akamaized.net; style-src 'self' 'unsafe-inline' fonts.googleapis.com; font-src 'self' fonts.gstatic.com; frame-src 'self'; object-src 'none'"
</Location>
```