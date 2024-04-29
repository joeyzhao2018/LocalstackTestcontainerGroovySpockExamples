package com.joeyzhao.demo

import org.testcontainers.containers.localstack.LocalStackContainer
import org.testcontainers.utility.DockerImageName
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.sns.SnsClient
import software.amazon.awssdk.services.sqs.SqsClient
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.services.sqs.model.QueueAttributeName
import spock.lang.Shared
import spock.lang.Specification

class LocalstackTestcontainerAWSSDKv2 extends  Specification {

    static final localstack = new LocalStackContainer(DockerImageName.parse("localstack/localstack:latest"))
    .withServices(LocalStackContainer.Service.SQS, LocalStackContainer.Service.SNS).withEnv("DEFAULT_REGION","us-east-1")

    @Shared SnsClient snsClient
    @Shared SqsClient sqsClient
    @Shared String testQueueURL
    @Shared String testQueueARN
    @Shared String testTopicARN


    void setup(){
        localstack.start()
        snsClient = SnsClient.builder()
                .endpointOverride(localstack.getEndpointOverride(LocalStackContainer.Service.SNS))
                .region(Region.of(localstack.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test")))
                .build()
        sqsClient = SqsClient.builder()
                .endpointOverride(localstack.getEndpointOverride(LocalStackContainer.Service.SQS))
                .region(Region.of(localstack.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test")))
                .build()
        testQueueURL = sqsClient.createQueue {  it.queueName("testqueue") }.queueUrl()
        testQueueARN = sqsClient.getQueueAttributes {it.queueUrl(testQueueURL).attributeNames(QueueAttributeName.QUEUE_ARN)}.attributes().get(QueueAttributeName.QUEUE_ARN)
        testTopicARN = snsClient.createTopic { it.name("testtopic") }.topicArn()
        snsClient.subscribe {it.topicArn(testTopicARN).protocol("sqs").endpoint(testQueueARN)}

    }

    def "SNS TO SQS"(){
        when:
        snsClient.publish { it.message("testingSecret").topicArn(testTopicARN)}
        def messages = sqsClient.receiveMessage {it.queueUrl(testQueueURL)}

        then:
        messages.messages().get(0).body().contains("testingSecret")
    }
}