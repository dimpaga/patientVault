# PatientVault Deployment Guide

## Overview
PatientVault is a Spring Boot application that can be deployed to containerized platforms like Render, Railway, or any Docker-compatible environment.

## Prerequisites
- Docker and Docker Compose (for local testing)
- Render.com account (or equivalent platform)
- PostgreSQL database (can use Render's PostgreSQL add-on)
- S3-compatible storage (AWS S3 or alternative)
- SMTP server for email notifications

## Deployment Files

### 1. Dockerfile
Multi-stage build that:
- Builds the application using Maven in the first stage
- Packages it into a lightweight JRE runtime in the second stage
- Sets up health checks for Render
- Runs the application with the `prod` Spring profile

### 2. .dockerignore
Optimizes Docker builds by excluding unnecessary files from the Docker context.

### 3. render.yaml
Render-specific configuration that defines:
- Service type and plan
- Database configuration
- Build and start commands
- Environment variables
- Health check endpoint

## Critical Configuration Steps

### 1. Database Setup
The app uses PostgreSQL with Flyway migrations. When deploying to Render:
- Render will automatically create the PostgreSQL database when you reference it in render.yaml
- Database migrations run automatically on startup via Flyway
- Ensure the database connection variables are properly set

### 2. Encryption Keys (CRITICAL!)
The application stores sensitive data encrypted. You MUST configure:

```
APP_MASTER_KEYS: Base64-encoded encryption keys (comma-separated for key rotation)
APP_ACTIVE_KEY_ID: ID of the currently active key
APP_BLIND_INDEX_KEY_ID: Key ID for search indexing
```

**DO NOT commit these to version control.** Set them in Render dashboard under Environment Variables.

To generate encryption keys:
```bash
# Generate a random 32-byte key and base64 encode it
openssl rand -base64 32
```

### 3. SMTP Configuration
Email is required for appointment reminders. Set in Render dashboard:

```
SMTP_HOST: Your mail server host
SMTP_PORT: Usually 587 (for TLS) or 465 (for SSL)
SMTP_USERNAME: Your email account
SMTP_PASSWORD: Your email password
SMTP_AUTH: true
MAIL_FROM: "PatientVault <noreply@your-domain.com>"
```

Recommended: Use SendGrid, Mailgun, or similar SMTP services rather than personal email.

### 4. S3 Storage Configuration
For storing patient files (images, PDFs, DICOM):

```
S3_BUCKET: Your bucket name
S3_REGION: eu-central-1 (or your preferred region)
S3_ACCESS_KEY_ID: AWS access key
S3_SECRET_ACCESS_KEY: AWS secret key
```

For S3-compatible services (MinIO, etc.):
```
S3_ENDPOINT: Your S3-compatible endpoint URL
```

### 5. Security Settings

#### Session Configuration
```
app.security.session.idle-timeout: 15m
app.security.session.absolute-timeout: 8h
```

#### Rate Limiting
Configured in application.yml:
```
app.security.rate-limit.attempts: 10
app.security.rate-limit.window: 1m
```

#### Password Policy
Minimum 8 characters, no common passwords, no username in password.

### 6. Bootstrap User (Initial Setup)
For creating the first user, temporarily set:

```
APP_BOOTSTRAP_USERNAME: doctor@example.com
APP_BOOTSTRAP_PASSWORD: Initial-Password-123
```

**Remove these after creating the user** to prevent security issues.

### 7. Application Configuration
Core settings that should be configured:

```
app.timezone: Europe/Athens  # Set to your timezone
app.validation.amka.strict-date: false  # Set to true for stricter Greek AMKA validation
app.uploads.max-file-size: 100MB  # Maximum file upload size
```

## Deployment Steps on Render

1. **Connect Repository**
   - Push these files to your Git repository
   - Connect your repository to Render

2. **Configure Environment**
   - Create a new Web Service on Render
   - Upload render.yaml or manually configure using the dashboard
   - Set all sensitive environment variables in Render dashboard

3. **Configure Database**
   - Create PostgreSQL database add-on
   - Ensure database environment variables are linked

4. **Configure Storage** (if using S3)
   - Create S3 bucket (or use existing)
   - Create IAM user with S3 access
   - Set S3 environment variables

5. **Configure Email**
   - Set up SMTP credentials
   - Set mail notification address
   - Test email sending via Settings

6. **Deploy**
   - Render will automatically build and deploy
   - Monitor build logs for Flyway migrations
   - Verify application starts and passes health checks

7. **Post-Deployment**
   - Remove bootstrap credentials
   - Test login with created user
   - Configure appointment reminders
   - Set up S3 sync if using scheduled imports

## Scaling Considerations

### Database
- Render's standard PostgreSQL plan is suitable for small to medium deployments
- Monitor connection pool: defaults to 10, configurable via HikariCP settings
- Enable automated backups in Render dashboard

### Application
- Spring Boot application is stateless (suitable for horizontal scaling)
- Session data stored in database (can scale to multiple instances)
- Reminder processing might need separate worker dyno if volume is high

### Storage
- S3 is infinitely scalable
- Monitor S3 costs for large file storage
- Consider lifecycle policies to archive old files

## Monitoring

### Health Checks
Render automatically monitors `/actuator/health/readiness` endpoint which checks:
- Database connectivity
- Flyway migration status
- S3 connectivity
- Encryption keys availability

### Logs
Monitor application logs in Render dashboard for:
- Database connection issues
- Failed email sends
- S3 upload failures
- Authentication issues

### Reminders
Application sends appointment reminders based on configured lead times:
```
app.reminders.lead-times: P1D,PT1H  # 1 day before, 1 hour before
```

## Troubleshooting

### Database Connection Failed
```
The server requested SCRAM-based authentication, but no password was provided.
```
- Ensure DB_PASSWORD environment variable is set
- Check database credentials in Render dashboard

### Encryption Key Error
```
Crypto service not initialized
```
- Verify APP_MASTER_KEYS and APP_ACTIVE_KEY_ID are set
- Keys must be base64-encoded
- Check key rotation configuration

### Email Sending Failed
- Verify SMTP credentials are correct
- Check firewall/port access (port 587 or 465)
- Test with different SMTP provider if issues persist
- Monitor application logs for SMTP errors

### S3 Connection Error
- Verify S3_ACCESS_KEY_ID and S3_SECRET_ACCESS_KEY are correct
- Check bucket region in S3_REGION
- Verify IAM user has s3:* permissions
- If using S3-compatible service, verify S3_ENDPOINT

## Development vs Production

### Local Development
Use docker-compose.yml which provides:
- PostgreSQL with auto-creation
- S3Mock for local S3 emulation
- Mailpit for email testing

```bash
docker-compose up -d
# Configure SPRING_PROFILES_ACTIVE=local
mvn spring-boot:run
```

### Production (Render)
- Application runs in Docker container
- SPRING_PROFILES_ACTIVE=prod (from render.yaml)
- Production logging: JSON format with ECS structure
- All connections use TLS/SSL

## Security Best Practices

1. **Never commit secrets** to version control
2. **Use Render's secret management** for all sensitive data
3. **Enable HTTPS** (Render provides automatic certificates)
4. **Rotate encryption keys** periodically (update APP_ACTIVE_KEY_ID)
5. **Monitor access logs** for suspicious activity
6. **Use strong passwords** for database and email accounts
7. **Restrict S3 bucket access** to minimum required permissions
8. **Enable database backups** in Render dashboard
9. **Implement rate limiting** to prevent brute force attacks
10. **Keep dependencies updated** (Maven, Spring Boot, security patches)

## Cost Estimation

Typical monthly costs on Render (approximate):
- Web Service: $7-25/month (depending on plan)
- PostgreSQL: $15-100/month (depending on size)
- S3 Storage: $0.023 per GB + transfer costs
- Email: $0-20/month (depends on volume and provider)

## References

- [Render Documentation](https://render.com/docs)
- [Spring Boot Production Guide](https://spring.io/guides/gs/spring-boot-docker/)
- [PostgreSQL Best Practices](https://www.postgresql.org/docs/current/index.html)
- [AWS S3 Documentation](https://docs.aws.amazon.com/s3/)
