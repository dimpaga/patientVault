FROM couchdb:3

COPY 10-patientvault.ini /opt/couchdb/etc/local.d/10-patientvault.ini

# Credentials come only from the COUCHDB_USER / COUCHDB_PASSWORD environment variables.
# In production a persistent disk must be mounted at /opt/couchdb/data.
