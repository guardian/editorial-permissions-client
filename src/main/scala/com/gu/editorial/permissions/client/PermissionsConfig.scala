package com.gu.editorial.permissions.client

import com.amazonaws.auth.{DefaultAWSCredentialsProviderChain, AWSCredentialsProvider}


case class PermissionsConfig(app: String,
                             all: Seq[Permission],
                             awsCredentials: AWSCredentialsProvider = new DefaultAWSCredentialsProviderChain,
                             s3Bucket: String = "permissions-cache",
                             s3BucketPrefix: String = "PROD",
                             s3PermissionsFile: String = "permissions.json",
                             s3Region: Option[String] = None,
                             enablePermissionsStore: Boolean = true)

