// Runs on the local Jenkins container (see jenkins/docker-compose.yml) -- this machine
// builds every artifact (Docker images, the frontend bundle); EC2 only ever receives
// finished artifacts and runs them, never builds anything itself. Deliberately keeps
// Jenkins itself minimal (just the Docker CLI + an SSH client, see jenkins/Dockerfile) --
// every tool-specific step below (Node, the AWS CLI) runs inside its own short-lived
// Docker container instead of being installed onto the Jenkins image directly, the same
// "Docker outside of Docker" pattern that lets this container talk to the host's daemon
// at all.
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

        // node:20-alpine, not Node installed on the Jenkins image itself -- same
        // ephemeral-container pattern as the AWS CLI step further down, so the Jenkins
        // image never needs updating just because the frontend's own tooling changes.
        stage('Frontend: lint + build') {
            when { environment name: 'BUILD_FRONTEND', value: 'true' }
            steps {
                sh 'docker run --rm -v "$WORKSPACE/frontend:/app" -w /app node:20-alpine sh -c "npm ci && npm run lint && npm run build"'
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
                sshagent(credentials: ['ec2-ssh-key']) {
                    script {
                        def services = []
                        if (env.BUILD_ORDER == 'true') services << 'order-service'
                        if (env.BUILD_INVENTORY == 'true') services << 'inventory-service'
                        if (env.BUILD_PAYMENT == 'true') services << 'payment-service'
                        if (env.BUILD_GATEWAY == 'true') services << 'api-gateway'

                        for (svc in services) {
                            echo "Shipping ${svc} to EC2..."
                            sh "docker save order-management-${svc}:latest | gzip | ssh -o StrictHostKeyChecking=accept-new ubuntu@${GATEWAY_HOST} 'gunzip | docker load'"
                        }

                        def serviceArgs = services.join(' ')
                        // api-gateway restarts every time something backend-side deployed,
                        // even when it wasn't itself rebuilt -- it caches the other
                        // services' internal Docker IPs and needs a kick to pick up fresh
                        // ones after they restart, the same real bug this project's own
                        // start-all.ps1 already works around for the exact same reason.
                        sh """
                            ssh -o StrictHostKeyChecking=accept-new ubuntu@${GATEWAY_HOST} '
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

        // --exclude "product-images/*" is not optional -- see deploy-frontend.ps1's own
        // comment on this exact flag for why: dist/ never contains admin-uploaded product
        // photos, and --delete syncing without this exclusion previously wiped every one
        // of them in production.
        stage('Deploy frontend') {
            when { environment name: 'BUILD_FRONTEND', value: 'true' }
            steps {
                withCredentials([usernamePassword(
                    credentialsId: 'aws-frontend-deploy',
                    usernameVariable: 'AWS_ACCESS_KEY_ID',
                    passwordVariable: 'AWS_SECRET_ACCESS_KEY'
                )]) {
                    sh '''
                        docker run --rm \
                          -e AWS_ACCESS_KEY_ID -e AWS_SECRET_ACCESS_KEY -e AWS_DEFAULT_REGION=${AWS_REGION} \
                          -v "$WORKSPACE/frontend/dist:/dist" \
                          amazon/aws-cli s3 sync /dist "s3://${FRONTEND_BUCKET}" --delete --exclude "product-images/*"

                        docker run --rm \
                          -e AWS_ACCESS_KEY_ID -e AWS_SECRET_ACCESS_KEY -e AWS_DEFAULT_REGION=${AWS_REGION} \
                          amazon/aws-cli cloudfront create-invalidation --distribution-id "${CLOUDFRONT_DISTRIBUTION_ID}" --paths "/*"
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
