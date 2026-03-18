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
