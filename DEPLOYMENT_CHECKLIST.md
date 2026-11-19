# Deployment Configuration Checklist

Complete this checklist before deploying PatientVault to Render or any production environment.

## Pre-Deployment Setup

### 1. Git Repository
- [ ] Push all code to your Git repository
- [ ] Include Dockerfile, .dockerignore, render.yaml
- [ ] Include DEPLOYMENT.md for reference
- [ ] Do NOT commit any .env or secret files

### 2. Render Account & Services Setup

#### PostgreSQL Database
- [ ] Create PostgreSQL database on Render
- [ ] Note the database connection details
- [ ] Enable automated backups
- [ ] Set backup retention to at least 7 days

#### Optional: S3 Storage
- [ ] Create AWS S3 bucket (or use S3-compatible service)
- [ ] Create IAM user with S3 access only
- [ ] Generate access keys for the IAM user
- [ ] Note bucket name and region

#### Optional: SMTP Service
- [ ] Choose SMTP provider (SendGrid, Mailgun, etc.)
- [ ] Create API credentials/SMTP credentials
- [ ] Note SMTP host, port, username, password

### 3. Encryption Key Generation

Generate encryption keys (MUST be done before first deployment):

```bash
# Generate primary key
openssl rand -base64 32  # Store as APP_MASTER_KEYS

# Generate a key ID (can be any string, e.g., "2024-11-19-primary")
# Store as APP_ACTIVE_KEY_ID

# Generate blind index key
openssl rand -base64 32  # Store as APP_BLIND_INDEX_KEY_ID
```

**Security Critical**: Store these values securely, never in version control!

## Render Configuration

### Environment Variables to Configure

Configure these in Render Dashboard > Environment > Environment Variables:

#### Database (Auto-linked from PostgreSQL add-on)
- Database URL is auto-configured by Render
- DB_HOST, DB_PORT, DB_NAME, DB_USER, DB_PASSWORD are linked

#### Required - Encryption
```
APP_MASTER_KEYS = [your-base64-key-from-above]
APP_ACTIVE_KEY_ID = [your-key-id]
APP_BLIND_INDEX_KEY_ID = [your-base64-key-from-above]
```

#### Required - Email
```
SMTP_HOST = [your-smtp-host]
SMTP_PORT = 587 or 465
SMTP_USERNAME = [your-email]
SMTP_PASSWORD = [your-password]
SMTP_AUTH = true
MAIL_FROM = PatientVault <noreply@your-domain.com>
```

#### Required - Initial User Setup (Remove after first login)
```
APP_BOOTSTRAP_USERNAME = doctor@example.com
APP_BOOTSTRAP_PASSWORD = TemporaryPassword123!
```

#### Optional - S3 Storage
```
S3_BUCKET = your-bucket-name
S3_REGION = eu-central-1
S3_ACCESS_KEY_ID = [your-access-key]
S3_SECRET_ACCESS_KEY = [your-secret-key]
```

For S3-compatible services:
```
S3_ENDPOINT = https://your-s3-endpoint.com
```

#### Optional - Custom Settings
```
app.timezone = Europe/Athens
app.security.session.idle-timeout = 15m
app.security.session.absolute-timeout = 8h
```

#### Optional - Doctor Notification Email
```
APP_DOCTOR_NOTIFICATION_EMAIL = doctor@example.com
```

#### Optional - Public URL for Reminder Links
```
APP_PUBLIC_BASE_URL = https://your-render-domain.onrender.com
```

## Deployment Steps

### Step 1: Connect Repository
1. Go to Render.com
2. Create new Web Service
3. Connect your GitHub/GitLab repository
4. Select the patientvault repository

### Step 2: Configure Service
- **Name**: patientvault
- **Runtime**: Docker
- **Build Command**: `docker build -t patientvault:latest .`
- **Start Command**: `java -Dspring.profiles.active=prod -jar app.jar`
- **Health Check Path**: `/actuator/health/readiness`

### Step 3: Configure Database
- Add PostgreSQL add-on
- Create new database
- Link database environment variables

### Step 4: Configure Environment Variables
- Add all required environment variables from the checklist above
- Use Render's secret management for sensitive values

### Step 5: Deploy
1. Click "Create Web Service"
2. Monitor build logs
3. Wait for health check to pass
4. Application will be deployed at: https://[your-service-name].onrender.com

## Post-Deployment Verification

### Immediate Checks (First 5 minutes)
- [ ] Application status is "Live" in Render dashboard
- [ ] No errors in application logs
- [ ] Health check endpoint returns 200 OK

### Database Verification
- [ ] Flyway migrations completed successfully
- [ ] Check application logs for "Flyway X migrations applied"
- [ ] Verify database tables were created

### Bootstrap User Verification
1. Navigate to https://[your-domain].onrender.com/login
2. Enter bootstrap username and password
3. If login succeeds:
   - [ ] Remove APP_BOOTSTRAP_USERNAME from environment variables
   - [ ] Remove APP_BOOTSTRAP_PASSWORD from environment variables
   - [ ] Redeploy application

### Email Configuration Verification
1. Go to Settings in the application
2. Enter a notification email address
3. Click "Send test e-mail"
4. [ ] Verify email is received

### S3 Configuration Verification (if enabled)
1. Upload a file to a patient record
2. [ ] File upload succeeds without errors
3. [ ] Check S3 bucket in AWS console for the file

## Production Hardening

### Security
- [ ] Remove all bootstrap credentials after first user creation
- [ ] Enable automatic updates for security patches
- [ ] Review and restrict S3 bucket public access
- [ ] Enable database encryption at rest (Render default)
- [ ] Enable HTTPS (Render automatic with Render domains)

### Monitoring
- [ ] Set up email alerts for failed deployments
- [ ] Monitor application logs daily for errors
- [ ] Check database connection pool usage
- [ ] Monitor S3 storage costs

### Backup & Recovery
- [ ] Enable automated database backups (7-day retention minimum)
- [ ] Test backup restoration process
- [ ] Document recovery procedures
- [ ] Store encryption keys securely offline

### Scaling
- [ ] Monitor memory/CPU usage
- [ ] Consider upgrading plan if consistently high usage
- [ ] Monitor database query performance
- [ ] Consider read replicas if heavy read workload

## Troubleshooting During Deployment

### Build Fails
Check Render build logs for:
- Maven compilation errors
- Missing dependencies
- Docker image pull failures

**Solution**: Review pom.xml and Dockerfile

### Runtime Crashes
Check Render runtime logs for:
- Database connection failures
- Encryption key configuration errors
- Port binding errors

**Solution**: Verify environment variables are set correctly

### Health Check Fails
Application health check at `/actuator/health/readiness` checks:
- Database connectivity
- Flyway migration status
- S3 connectivity (if configured)

**Solution**: Check database connection and encryption keys

### Database Migration Fails
If Flyway migrations fail on first deploy:

**Check**: 
- Database user permissions
- PostgreSQL version compatibility (requires 11+)
- Special characters in migration SQL

**Solution**: Review flyway migration files in `src/main/resources/db/migration/`

## Rollback Plan

If deployment has critical issues:

1. In Render dashboard, select a previous successful deployment
2. Click "Redeploy" to rollback
3. Verify application status
4. Database will remain at current version (safe)

## Support

For deployment issues, check:
- [DEPLOYMENT.md](./DEPLOYMENT.md) - Detailed deployment guide
- Application logs in Render dashboard
- Database logs in PostgreSQL console
- [Render Documentation](https://render.com/docs)
