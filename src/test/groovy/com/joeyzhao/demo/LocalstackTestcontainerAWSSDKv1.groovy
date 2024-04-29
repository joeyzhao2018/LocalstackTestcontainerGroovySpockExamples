package com.joeyzhao.demo

import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.client.builder.AwsClientBuilder
import org.testcontainers.containers.localstack.LocalStackContainer
import org.testcontainers.containers.Network;

import org.testcontainers.utility.DockerImageName
import com.amazonaws.services.sns.AmazonSNSClientBuilder
import com.amazonaws.services.sqs.AmazonSQSClientBuilder
import com.amazonaws.services.sqs.model.GetQueueAttributesRequest
import com.amazonaws.services.sns.AmazonSNSClient
import com.amazonaws.services.sqs.AmazonSQSClient
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.sqs.SqsClient
import spock.lang.Shared
import spock.lang.Specification

import static org.testcontainers.containers.localstack.LocalStackContainer.Service.SNS
import static org.testcontainers.containers.localstack.LocalStackContainer.Service.SQS


class LocalstackTestcontainerAWSSDKv1 extends  Specification {
    static Network network = Network.newNetwork()
    static final localstack = new LocalStackContainer(DockerImageName.parse("localstack/localstack:latest"))
            .withServices( SQS, SNS)

    @Shared AmazonSNSClient snsClient
//    @Shared AmazonSQSClient sqsClient
    @Shared SqsClient sqsClient

    @Shared String testQueueURL
    @Shared String testQueueARN = "arn:aws:sqs:us-east-1:000000000000:testqueue"
    @Shared String testTopicARN


    void setup(){
        localstack.start()
//        String oversqs = localstack.getEndpointOverride(SNS).toString()
//        String hardcoded = "http://127.0.0.1:4566"
        snsClient = AmazonSNSClientBuilder.standard()
                .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration( localstack.getEndpointOverride(SNS).toString(), localstack.getRegion()))
                .withCredentials( new AWSStaticCredentialsProvider(new BasicAWSCredentials("test", "test")))
                .build()
        sqsClient = SqsClient.builder()
                .endpointOverride(localstack.getEndpointOverride(SQS))
                .region(Region.of(localstack.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test")))
                .build()
//        testQueueURL = sqsClient.createQueue("testqueue").queueUrl
//        testQueueARN = sqsClient.getQueueAttributes(new GetQueueAttributesRequest().withQueueUrl(testQueueURL).withAttributeNames("QueueARN"))
        testQueueURL = sqsClient.createQueue {  it.queueName("testqueue") }.queueUrl()

        testTopicARN = snsClient.createTopic("testtopic").topicArn
        snsClient.subscribe(testTopicARN, "sqs", testQueueARN)

    }

    def "SNS TO SQS"(){
        when:
        snsClient.publish(testTopicARN, "testingSecret")
        def messages = sqsClient.receiveMessage {it.queueUrl(testQueueURL)}

//        def messages = sqsClient.receiveMessage(testQueueURL)

        then:
        messages.messages().get(0).body().contains("testingSecret")
    }


}