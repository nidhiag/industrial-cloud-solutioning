/**
 * Copyright 2015 IBM
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
/**
 * Licensed Materials - Property of IBM
 * (c) Copyright IBM Corp. 2015
 */
package com.messagehub.samples;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutionException;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.messagehub.samples.env.CreateTopicConfig;
import com.messagehub.samples.env.CreateTopicParameters;
import com.messagehub.samples.env.MessageHubCredentials;
import com.messagehub.samples.env.MessageHubEnvironment;
import com.messagehub.samples.env.MessageList;

/**
 * Sample used for interacting with Message Hub over Secure Kafka / Kafka Native
 * channels.
 *
 * @author IBM
 */
public class MessageHubJavaSample {

    private static final String JAAS_CONFIG_PROPERTY = "java.security.auth.login.config";
    private static final long HOUR_IN_MILLISECONDS = 3600000L;
    private static final Logger logger = Logger.getLogger(MessageHubJavaSample.class);
    private static String userDir, resourceDir;
    private static boolean isDistribution;
    private String topic="";
    private String kafkaHost=null;
    private boolean closing = false;
    private String kafkaHostUS = "kafka01-prod01.messagehub.services.us-south.bluemix.net:9093,"
    		                  + "kafka02-prod01.messagehub.services.us-south.bluemix.net:9093,"
    		                  + "kafka03-prod01.messagehub.services.us-south.bluemix.net:9093,"
    		                  + "kafka04-prod01.messagehub.services.us-south.bluemix.net:9093,"
    		                  + "kafka05-prod01.messagehub.services.us-south.bluemix.net:9093";
    
    private String kafkaHostEU="kafka01-prod02.messagehub.services.eu-gb.bluemix.net:9093,"
    		                   +"kafka02-prod02.messagehub.services.eu-gb.bluemix.net:9093,"
    		                   +"kafka03-prod02.messagehub.services.eu-gb.bluemix.net:9093,"
    		                   +"kafka04-prod02.messagehub.services.eu-gb.bluemix.net:9093,"
    		                   +"kafka05-prod02.messagehub.services.eu-gb.bluemix.net:9093";
    
    private String restHost = null;
    private String apiKey = null;
    private KafkaProducer<byte[], byte[]> kafkaProducer;
    private KafkaConsumer<byte[], byte[]> kafkaConsumer;
    ArrayList<String> topicList;
    public MessageHubJavaSample(){
    	topicList = new ArrayList<String>();
    }
    
 
    public MessageHubJavaSample(String topicName){
    	 this.topic=topicName;
         String vcapServices = System.getenv("VCAP_SERVICES");
         ObjectMapper mapper = new ObjectMapper();
         System.out.println("vcapServices::" +vcapServices);
         System.setProperty("java.security.auth.login.config", "");
         if(vcapServices != null) {
             try {
                 // Parse VCAP_SERVICES into Jackson JsonNode, then map the 'messagehub' entry
                 // to an instance of MessageHubEnvironment.
                 JsonNode vcapServicesJson = mapper.readValue(vcapServices, JsonNode.class);
                 ObjectMapper envMapper = new ObjectMapper();
                 String vcapKey = null;
                 Iterator<String> it = vcapServicesJson.fieldNames();

                 // Find the Message Hub service bound to this application.
                 while (it.hasNext() && vcapKey == null) {
                     String potentialKey = it.next();

                     if (potentialKey.startsWith("messagehub")) {
                         logger.log(Level.INFO, "Using the '" + potentialKey + "' key from VCAP_SERVICES.");
                         vcapKey = potentialKey;
                     }
                 }

                 if (vcapKey == null) {
                     logger.log(Level.ERROR,
                                "Error while parsing VCAP_SERVICES: A Message Hub service instance is not bound to this application.");
                     return;
                 }
                 
                 MessageHubEnvironment messageHubEnvironment = envMapper.readValue(vcapServicesJson.get(vcapKey).get(0).toString(), MessageHubEnvironment.class);
                 MessageHubCredentials credentials = messageHubEnvironment.getCredentials();

                 kafkaHost = credentials.getKafkaBrokersSasl()[0];
                 restHost = credentials.getKafkaRestUrl();
                 apiKey = credentials.getApiKey();

                 System.out.println("kafkaHost::  "+kafkaHost + "restHost:: " +restHost + "apiKey:: " +apiKey);
 
                 //updateJaasConfiguration(credentials);
             } catch(final Exception e) {
                 e.printStackTrace();
                 return;
             }
         }
     }
    
    
      //public void InjestData(String data) throws InterruptedException,
      public void InjestData(MessageList list) throws InterruptedException,
            ExecutionException, IOException {
          String fieldName = "records";
          // Push a message into the list to be sent.
          //MessageList list = new MessageList();
         // list.push(data);
          System.out.println("3:: ++++++++++++Inside Ingest JSON data:: " +list.toString());
        
          try {
              if(apiKey==null){//For Local Testing
             	 apiKey="br1XTccWWjOvzxivvAXdqbmokRDlG9QitbwA4ddOquRkSmej";
             	 kafkaHost=kafkaHostEU;
              }
        	  System.out.println("DataInjestApp****************kafkaHost:: " +kafkaHost);
        	  System.out.println("DataInjestApp****************apiKey:: " +apiKey);
        	  Properties clientConfig=getClientConfiguration(kafkaHost, apiKey, true);
              this.kafkaProducer = new KafkaProducer<byte[], byte[]>(clientConfig);
              System.out.println("++++++++topic::  "+topic);
              // Create a producer record which will be sent
              // to the Message Hub service, providing the topic
              // name, field name and message. The field name and
              // message are converted to UTF-8.
              ProducerRecord<byte[], byte[]> record = new ProducerRecord<byte[], byte[]>(
                  topic,
                  list.toString().getBytes("UTF-8"));

              System.out.println("Message Sent!!!");
              // Synchronously wait for a response from Message Hub / Kafka.
              RecordMetadata m = kafkaProducer.send(record).get();
              System.out.println("Message produced, offset: " + m.offset());
              logger.log(Level.INFO, "Message produced, offset: " + m.offset());
          } catch (final Exception e) {
              e.printStackTrace();
              // Consumer will hang forever, so exit program.
              System.exit(-1);
          }
 
    }

      //public void InjestData(String data) throws InterruptedException,
      public void InjestStringData(String data) throws InterruptedException,
            ExecutionException, IOException {
          String fieldName = "records";
          // Push a message into the list to be sent.
          System.out.println("3:: ++++++++++++Inside Ingest String data:: " +data);
          try {
              if(apiKey==null){//For Local Testing
             	 apiKey="br1XTccWWjOvzxivvAXdqbmokRDlG9QitbwA4ddOquRkSmej";
             	 kafkaHost=kafkaHostEU;
              }
        	  System.out.println("DataInjestApp****************kafkaHost:: " +kafkaHost);
        	  System.out.println("DataInjestApp****************apiKey:: " +apiKey);
        	  Properties clientConfig=getClientConfiguration(kafkaHost, apiKey, true);
              this.kafkaProducer = new KafkaProducer<byte[], byte[]>(clientConfig);
              System.out.println("++++++++topic::  "+topic);
              // Create a producer record which will be sent
              // to the Message Hub service, providing the topic
              // name, field name and message. The field name and
              // message are converted to UTF-8.
              ProducerRecord<byte[], byte[]> record = new ProducerRecord<byte[], byte[]>(
                  topic,
                  fieldName.getBytes("UTF-8"),
                  data.getBytes("UTF-8"));

              System.out.println("Message Sent!!!");
              // Synchronously wait for a response from Message Hub / Kafka.
              RecordMetadata m = kafkaProducer.send(record).get();
              System.out.println("Message produced, offset: " + m.offset());
              logger.log(Level.INFO, "Message produced, offset: " + m.offset());
          } catch (final Exception e) {
        	  System.out.println("(Inside Exception) Program Exit because of Message Hub Issue:: ");
              e.printStackTrace();
              // Consumer will hang forever, so exit program.
              
              System.exit(-1);
          }
 
    }

      public List checkTopic(String topicName) throws InterruptedException,
            ExecutionException, IOException {
    	  ArrayList topicData=new ArrayList();
          try {
        	  topicList.add(topic);
        	 
              if(apiKey==null){//For Local Testing
             	 apiKey="rwL9Gv9FmIZwpoYqUeVhFGdeePndAvOQp8oPOXzWbsyLUBEh";
             	 kafkaHost=kafkaHostEU;
              }
        	  System.out.println("DataInjestApp****************kafkaHost:: " +kafkaHost);
        	  System.out.println("DataInjestApp****************apiKey:: " +apiKey);
        	  Properties clientConfig=getClientConfiguration(kafkaHost, apiKey, true);
              this.kafkaConsumer = new KafkaConsumer<byte[], byte[]>(clientConfig,new ByteArrayDeserializer(), new ByteArrayDeserializer());
              kafkaConsumer.subscribe(topicList);
              // Poll on the Kafka consumer every second.
              Iterator<ConsumerRecord<byte[], byte[]>> it = kafkaConsumer
                      .poll(1000).iterator();

              // Iterate through all the messages received and print their
              // content.
              // After a predefined number of messages has been received, the
              // client
              // will exit.
              while (it.hasNext()) {
                  ConsumerRecord<byte[], byte[]> record = it.next();
                  final String message = new String(record.value(),
                          Charset.forName("UTF-8"));
                  System.out.println("Message:: " +message);
                  topicData.add(message);
                  logger.log(Level.INFO, "Message: " + message);
              }

              kafkaConsumer.commitSync();

              Thread.sleep(1000);              
          } catch (final InterruptedException e) {
              logger.log(Level.ERROR, "Producer/Consumer loop has been unexpectedly interrupted");
              shutdown();
          } catch (final Exception e) {
              logger.log(Level.ERROR, "Consumer has failed with exception: " + e);
              shutdown();
          }
          kafkaConsumer.close();
          return topicData;
    }

      public void shutdown() {
          closing = true;
      }
      
    /**
     * Retrieve client configuration information, using a properties file, for
     * connecting to secure Kafka.
     *
     * @param broker
     *            {String} A string representing a list of brokers the producer
     *            can contact.
     * @param apiKey
     *            {String} The API key of the Bluemix Message Hub service.
     * @param isProducer
     *            {Boolean} Flag used to determine whether or not the
     *            configuration is for a producer.
     * @return {Properties} A properties object which stores the client
     *         configuration info.
     */
    public final Properties getClientConfiguration(String broker,
            String apiKey, boolean isProducer) {
        Properties props = new Properties();
        InputStream propsStream;
        String fileName= "resources/producer.properties";

        try {
        	ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        	propsStream = classLoader.getResourceAsStream(fileName);
            props.load(propsStream);
            propsStream.close();
        } catch (IOException e) {
            logger.log(Level.ERROR, "Could not load properties from file");
            return props;
        }

        System.out.println("+++++++++++++++++++++++++++bootstrap.servers:: " +broker);
        props.put("bootstrap.servers", broker);

        return props;
    }
}
