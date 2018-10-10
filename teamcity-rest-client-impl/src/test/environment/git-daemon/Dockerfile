FROM alpine:3.7

RUN apk add --no-cache --update git-daemon tini && rm -rf /var/cache/apk/* && \
    for repo in test.git repo1.git repo2.git; \
        do  mkdir -p /git/$repo; \
            cd /git/$repo; \
            git init --bare .; \
            git config receive.denyDeleteCurrent false; \
            git config receive.denyCurrentBranch ignore; \
            cd ..; \
        done

ENTRYPOINT ["/sbin/tini", "--"]
CMD git daemon --export-all --base-path=/git --enable=upload-pack --enable=receive-pack --listen=0.0.0.0 --port=9418
