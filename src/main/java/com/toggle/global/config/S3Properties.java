package com.toggle.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cloud.aws")
public class S3Properties {

    private Credentials credentials = new Credentials();
    private String region;
    private S3Bucket s3 = new S3Bucket();

    public S3Properties() {
    }

    public S3Properties(Credentials credentials, String region, S3Bucket s3) {
        this.credentials = credentials;
        this.region = region;
        this.s3 = s3;
    }

    public Credentials credentials() {
        return credentials;
    }

    public void setCredentials(Credentials credentials) {
        this.credentials = credentials;
    }

    public String region() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public S3Bucket s3() {
        return s3;
    }

    public void setS3(S3Bucket s3) {
        this.s3 = s3;
    }

    public static class Credentials {
        private String accessKey;
        private String secretKey;

        public Credentials() {
        }

        public Credentials(String accessKey, String secretKey) {
            this.accessKey = accessKey;
            this.secretKey = secretKey;
        }

        public String accessKey() {
            return accessKey;
        }

        public void setAccessKey(String accessKey) {
            this.accessKey = accessKey;
        }

        public String secretKey() {
            return secretKey;
        }

        public void setSecretKey(String secretKey) {
            this.secretKey = secretKey;
        }
    }

    public static class S3Bucket {
        private String bucket;

        public S3Bucket() {
        }

        public S3Bucket(String bucket) {
            this.bucket = bucket;
        }

        public String bucket() {
            return bucket;
        }

        public void setBucket(String bucket) {
            this.bucket = bucket;
        }
    }
}
