// Runs on the local Jenkins container (see jenkins/docker-compose.yml) -- this machine
// builds every artifact (Docker images, the frontend bundle); EC2 only ever receives
// finished artifacts and runs them, never builds anything itself.
//
// Node and the AWS CLI are installed directly on the Jenkins image (see
// jenkins/Dockerfile), not run via ephemeral `docker run -v $WORKSPACE/...` containers --
// that was the original design, abandoned after an actual pipeline run proved it broken:
// a bind mount from inside this Docker-outside-of-Docker setup is resolved by the HOST's
// daemon, which has no idea what this container's own $WORKSPACE path means, and
// silently mounts an empty directory instead. `docker build`/`docker save` (the backend
// image stages, and the deploy stage) don't have this problem -- the CLI reads the local
// filesystem itself and streams the content over the API, no shared-path assumption
// involved -- so Docker itself stays install-directly-on-the-image too, just for a
// different, unaffected reason (see jenkins/Dockerfile's own comment).
//
// Deploying is a manual, explicit decision (see the "Approve deploy" stage) -- every
// build compiles and packages automatically, but nothing reaches EC2 or S3 without a
// human clicking "Deploy" in the Jenkins UI first.
pipeline {
    agent any

    environment {
        // The Gateway's stable DuckDNS hostname, not a raw IP -- this EC2 instance has no
        // Elastic IP and gets a new public IP on every restart (see DEPLOYMENT.md's cost
        // section for why), but start-all.ps1 already keeps this hostname pointed at
        // whatever that IP currently is. SSH-ing to the hostname means this pipeline
        // never needs to look up or hardcode an IP that could be stale by the next run.
        GATEWAY_HOST = 'nithish-ordermgmt.duckdns.org'
        FRONTEND_BUCKET = 'order-management-frontend-244689414185'
        CLOUDFRONT_DISTRIBUTION_ID = 'E1MH9X3BUX6CH5'
        // Not secrets -- see frontend/.env.example's own comment: the Cognito app
        // client has no client secret, and the Gateway URL is necessarily public
        // anyway (it's what the browser calls). Safe to hardcode here the same way
        // docker-compose.prod.yml already hardcodes this exact client ID for the
        // Gateway's own side of the same Cognito app client.
        VITE_COGNITO_REGION = 'eu-west-1'
        VITE_COGNITO_CLIENT_ID = '48v29rgvebkkaj46cjuo4vqjb5'
        VITE_COGNITO_DOMAIN = 'https://nithish-ordermgmt-auth.auth.eu-west-1.amazoncognito.com'
        AWS_REGION = 'eu-west-1'
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        // Avoids shipping all 4 (large) backend images and the frontend bundle on every
        // single build regardless of what actually changed -- a one-line frontend fix
        // shouldn't mean re-uploading 4 Docker images over this machine's own upload
        // bandwidth to EC2. GIT_PREVIOUS_SUCCESSFUL_COMMIT is nothing (null) on this
        // job's very first run, or after Jenkins loses that history -- treated as "build
        // and deploy everything," the safe default when there's no prior state to diff
        // against.
        stage('Detect changed services') {
            steps {
                script {
                    def baseCommit = env.GIT_PREVIOUS_SUCCESSFUL_COMMIT
                    def buildAll = (baseCommit == null)
                    def changed = buildAll ? '' : sh(script: "git diff --name-only ${baseCommit} HEAD", returnStdout: true).trim()

                    env.BUILD_ORDER = (buildAll || changed.contains('order-service/')) ? 'true' : 'false'
                    env.BUILD_INVENTORY = (buildAll || changed.contains('inventory-service/')) ? 'true' : 'false'
                    env.BUILD_PAYMENT = (buildAll || changed.contains('payment-service/')) ? 'true' : 'false'
                    env.BUILD_GATEWAY = (buildAll || changed.contains('api-gateway/')) ? 'true' : 'false'
                    env.BUILD_FRONTEND = (buildAll || changed.contains('frontend/')) ? 'true' : 'false'

                    echo "order=${env.BUILD_ORDER} inventory=${env.BUILD_INVENTORY} payment=${env.BUILD_PAYMENT} gateway=${env.BUILD_GATEWAY} frontend=${env.BUILD_FRONTEND}"
                }
            }
        }

        // Each Dockerfile is a multi-stage build that runs `mvn package` itself (see
        // e.g. order-service/Dockerfile) -- this doubles as the "does it compile" check;
        // a real test suite would get its own stage here once one exists (see README's
        // Known Limitations).
        stage('Build backend images') {
            parallel {
                stage('order-service') {
                    when { environment name: 'BUILD_ORDER', value: 'true' }
                    steps {
                        sh 'docker build -t order-management-order-service:latest -f order-service/Dockerfile order-service'
                    }
                }
                stage('inventory-service') {
                    when { environment name: 'BUILD_INVENTORY', value: 'true' }
                    steps {
                        sh 'docker build -t order-management-inventory-service:latest -f inventory-service/Dockerfile inventory-service'
                    }
                }
                stage('payment-service') {
                    when { environment name: 'BUILD_PAYMENT', value: 'true' }
                    steps {
                        sh 'docker build -t order-management-payment-service:latest -f payment-service/Dockerfile payment-service'
                    }
                }
                stage('api-gateway') {
                    when { environment name: 'BUILD_GATEWAY', value: 'true' }
                    steps {
                        sh 'docker build -t order-management-api-gateway:latest -f api-gateway/Dockerfile api-gateway'
                    }
                }
            }
        }

        // Runs directly (Node is installed on the Jenkins image itself -- see
        // jenkins/Dockerfile), NOT via an ephemeral `docker run -v $WORKSPACE/...`
        // container the way the backend images build. Found on a real run why that
        // doesn't work: a bind mount from inside a Docker-outside-of-Docker container is
        // resolved by the HOST daemon, which has no idea what this container's own
        // $WORKSPACE path even means -- it silently mounted an empty directory instead
        // of the checked-out frontend code.
        stage('Frontend: lint + build') {
            when { environment name: 'BUILD_FRONTEND', value: 'true' }
            steps {
                dir('frontend') {
                    // frontend/.env is gitignored (it's where real per-developer values
                    // live -- see .env.example), so it simply doesn't exist in this
                    // fresh Jenkins checkout. Found the hard way, in production, not by
                    // review: without it, Vite bakes in literal `undefined` for every
                    // VITE_* reference, so bffLogin's fetch call becomes
                    // fetch("undefined/auth/login") -- a relative URL the browser
                    // resolves against the CloudFront origin itself, landing on the
                    // SPA's own index.html instead of the real API. That HTML, handed
                    // to response.json(), is exactly "Unexpected token '<'".
                    sh '''
                        cat > .env << EOF
VITE_GATEWAY_URL=https://${GATEWAY_HOST}
VITE_COGNITO_REGION=${VITE_COGNITO_REGION}
VITE_COGNITO_CLIENT_ID=${VITE_COGNITO_CLIENT_ID}
VITE_COGNITO_DOMAIN=${VITE_COGNITO_DOMAIN}
EOF
                        npm ci && npm run lint && npm run build
                    '''
                }
            }
        }

        // The one manual gate in the whole pipeline -- everything above happens on every
        // build automatically; nothing below happens without a human actually clicking
        // "Deploy" here. Skipped entirely (no gate, no deploy stages run) on a build
        // where nothing relevant changed at all.
        stage('Approve deploy') {
            when {
                anyOf {
                    environment name: 'BUILD_ORDER', value: 'true'
                    environment name: 'BUILD_INVENTORY', value: 'true'
                    environment name: 'BUILD_PAYMENT', value: 'true'
                    environment name: 'BUILD_GATEWAY', value: 'true'
                    environment name: 'BUILD_FRONTEND', value: 'true'
                }
            }
            steps {
                input message: "Deploy build #${env.BUILD_NUMBER} to production?", ok: 'Deploy'
            }
        }

        // No container registry involved -- `docker save` streams the image as a tar
        // straight over the same SSH connection already used for everything else this
        // project does with EC2, `docker load` on the other end unpacks it directly into
        // that host's own image store. Simpler than standing up and authenticating
        // against a registry for a single-target deploy, at the cost of re-transferring
        // the full image every time (no registry-side layer caching) -- a reasonable
        // trade for this project's scale.
        //
        // git pull still runs on EC2 even though the application code itself arrives as
        // a prebuilt image now -- docker-compose.prod.yml, the Postgres init script, and
        // this Jenkinsfile all still live in that checkout and need to stay current too.
        stage('Deploy backend') {
            when {
                anyOf {
                    environment name: 'BUILD_ORDER', value: 'true'
                    environment name: 'BUILD_INVENTORY', value: 'true'
                    environment name: 'BUILD_PAYMENT', value: 'true'
                    environment name: 'BUILD_GATEWAY', value: 'true'
                }
            }
            steps {
                // withCredentials + sshUserPrivateKey, not the sshagent() step -- that
                // needs the separate "SSH Agent" plugin, which turned out not to be
                // installed (a real NoSuchMethodError on an actual run, not something
                // caught by review). This binds the same 'ec2-ssh-key' credential to a
                // temporary key FILE instead of an agent process, using only
                // Credentials Binding, which is already in play for the AWS credentials
                // below -- no extra plugin to install.
                withCredentials([sshUserPrivateKey(
                    credentialsId: 'ec2-ssh-key',
                    keyFileVariable: 'SSH_KEY',
                    usernameVariable: 'SSH_USER'
                )]) {
                    script {
                        def services = []
                        if (env.BUILD_ORDER == 'true') services << 'order-service'
                        if (env.BUILD_INVENTORY == 'true') services << 'inventory-service'
                        if (env.BUILD_PAYMENT == 'true') services << 'payment-service'
                        if (env.BUILD_GATEWAY == 'true') services << 'api-gateway'

                        for (svc in services) {
                            echo "Shipping ${svc} to EC2..."
                            sh "docker save order-management-${svc}:latest | gzip | ssh -i \"\$SSH_KEY\" -o StrictHostKeyChecking=accept-new \$SSH_USER@${GATEWAY_HOST} 'gunzip | docker load'"
                        }

                        def serviceArgs = services.join(' ')
                        // api-gateway restarts every time something backend-side deployed,
                        // even when it wasn't itself rebuilt -- it caches the other
                        // services' internal Docker IPs and needs a kick to pick up fresh
                        // ones after they restart, the same real bug this project's own
                        // start-all.ps1 already works around for the exact same reason.
                        sh """
                            ssh -i "\$SSH_KEY" -o StrictHostKeyChecking=accept-new \$SSH_USER@${GATEWAY_HOST} '
                                cd order-management &&
                                git pull &&
                                sudo docker compose -f docker-compose.prod.yml up -d ${serviceArgs} &&
                                sudo docker compose -f docker-compose.prod.yml restart api-gateway
                            '
                        """
                    }
                }
            }
        }

        // --exclude "product-images/*" AND "product-videos/*" are not optional -- see
        // deploy-frontend.ps1's own comment on this exact flag for why: dist/ never
        // contains admin-uploaded product photos or videos, and --delete syncing without
        // excluding a prefix wipes every file under it in production. Learned this the
        // hard way twice now: once for product-images/ (fixed then), and again for
        // product-videos/ when it shipped without being added to this same exclusion --
        // an admin's uploaded video was silently deleted by the very next frontend
        // deploy, even though nothing about that deploy touched video code at all.
        //
        // Runs the AWS CLI directly (installed on the Jenkins image -- see
        // jenkins/Dockerfile), not via an ephemeral container bind-mounting dist/ -- same
        // Docker-outside-of-Docker bind-mount problem as the frontend build stage above,
        // just one step later in the pipeline.
        stage('Deploy frontend') {
            when { environment name: 'BUILD_FRONTEND', value: 'true' }
            steps {
                withCredentials([usernamePassword(
                    credentialsId: 'aws-frontend-deploy',
                    usernameVariable: 'AWS_ACCESS_KEY_ID',
                    passwordVariable: 'AWS_SECRET_ACCESS_KEY'
                )]) {
                    sh '''
                        aws s3 sync frontend/dist "s3://${FRONTEND_BUCKET}" --delete --exclude "product-images/*" --exclude "product-videos/*"
                        aws cloudfront create-invalidation --distribution-id "${CLOUDFRONT_DISTRIBUTION_ID}" --paths "/*"
                    '''
                }
            }
        }
    }

    post {
        success {
            echo "Build #${env.BUILD_NUMBER} finished successfully."
        }
        failure {
            echo "Build #${env.BUILD_NUMBER} failed -- nothing past the failing stage was deployed."
        }
    }
}
