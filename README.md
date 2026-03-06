# cloudflare-javadoc-deploy

AWS Lambda function that, for a given list of Maven packages (`groupId` and `artifactId`), downloads Javadoc JARs, prepares a static site bundle with `index.html` pages for navigation, and deploys it to Cloudflare Pages.

Currently used for: https://javadoc.akshitbansal.co.in

## Required Environment Variables At Runtime
- `CLOUDFLARE_API_TOKEN`
- `CLOUDFLARE_PROJECT_NAME`
