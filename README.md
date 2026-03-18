# cloudflare-javadoc-deploy

AWS Lambda function that, for a given list of Maven packages (`groupId` and `artifactId`), downloads Javadoc JARs, prepares a static site bundle with `index.html` pages for navigation, and deploys it to Cloudflare Pages.

Currently used for: https://javadoc.akshitbansal.co.in

## Environment Variables At Runtime
Required when Cloudflare deployment is enabled (default):
- `CLOUDFLARE_API_TOKEN`
- `CLOUDFLARE_PROJECT_NAME`

Required when status email is enabled (default):
- `STATUS_EMAIL_RECIPIENT`
- `STATUS_EMAIL_SENDER`
- `SITE_URL`

Optional flags (all default to `false`):
- `DISABLE_SNAPSHOTS`
- `DISABLE_CLOUDFLARE_DEPLOYMENT`
- `DISABLE_TEMP_FILE_DELETION`
- `DISABLE_STATUS_EMAIL`

## Local Testing
The Makefile is intended for local testing (builds the image and runs the Lambda container locally).
You can override Makefile variables by creating a `secrets.mk` file, which the Makefile already includes optionally.

Example `secrets.mk`:
```make
CLOUDFLARE_API_TOKEN = your-token
CLOUDFLARE_PROJECT_NAME = your-project
AWS_ACCESS_KEY_ID = your-access-key
AWS_SECRET_ACCESS_KEY = your-secret-key
STATUS_EMAIL_RECIPIENT = you@example.com
STATUS_EMAIL_SENDER = bot@example.com
SITE_URL = https://example.com
```
