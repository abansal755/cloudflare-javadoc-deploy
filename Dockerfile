FROM public.ecr.aws/lambda/java:21

# Install Node 20
RUN curl -fsSL https://rpm.nodesource.com/setup_20.x | bash - \
    && dnf install -y nodejs \
    && dnf clean all

# Install Wrangler globally
RUN npm install -g wrangler

# Copy function code and runtime dependencies from Maven layout
COPY target/classes ${LAMBDA_TASK_ROOT}
COPY target/dependency/* ${LAMBDA_TASK_ROOT}/lib/

# Set the CMD to your handler (could also be done as a parameter override outside of the Dockerfile)
CMD [ "in.co.akshitbansal.cloudflare.javadoc.deploy.LambdaHandler::handleRequest" ]