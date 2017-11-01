package i2am.benchmark.spark.bloom;

import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.log4j.Logger;
import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.broadcast.Broadcast;
import org.apache.spark.streaming.Durations;
import org.apache.spark.streaming.api.java.JavaDStream;
import org.apache.spark.streaming.api.java.JavaInputDStream;
import org.apache.spark.streaming.api.java.JavaStreamingContext;
import org.apache.spark.streaming.kafka010.ConsumerStrategies;
import org.apache.spark.streaming.kafka010.KafkaUtils;
import org.apache.spark.streaming.kafka010.LocationStrategies;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;


public class SparkBloomFilterTest {	

	private final static Logger logger = Logger.getLogger(SparkBloomFilterTest.class);
	private static JedisPool pool;
		
	public static void main(String[] args) throws InterruptedException, UnsupportedEncodingException {

		// Args.
		String input_topic = args[0];
		String output_topic = args[1];
		String group = args[2];
		long duration = Long.valueOf(args[3]);

		// MN.eth 9092.
		String zookeeper_ip = args[4];
		String zookeeper_port = args[5];
		String zk = zookeeper_ip + ":" + zookeeper_port;

		// Filtering Keywords.		
		String redis_key = args[6];		
		int bloom_size = Integer.parseInt(args[7]);		
		String[] input_keywords = args.clone();
		String[] keywords = Arrays.copyOfRange(input_keywords, 8, input_keywords.length);		

		// Make Bloom Filter.
		BloomFilter bloom = new BloomFilter(bloom_size);		
		for( String keyword: keywords ) {			
			bloom.registData(keyword);			
		}
		
		// Context.
		SparkConf conf = new SparkConf().setAppName("kafka-test");
		JavaSparkContext sc = new JavaSparkContext(conf);
		JavaStreamingContext jssc = new JavaStreamingContext(sc, Durations.milliseconds(duration));

		// BroadCast Variables
		Broadcast<BloomFilter> bloom_filter = sc.broadcast(bloom);

		// Kafka Parameter.
		Map<String, Object> kafkaParams = new HashMap<>();
		kafkaParams.put("bootstrap.servers", zk);
		kafkaParams.put("key.deserializer", StringDeserializer.class);
		kafkaParams.put("value.deserializer", StringDeserializer.class);
		kafkaParams.put("group.id", group);
		kafkaParams.put("auto.offset.reset", "earliest");
		kafkaParams.put("enable.auto.commit", false);

		// Make Kafka Producer.		
		Properties props = new Properties();
		props.put("bootstrap.servers", zk);
		props.put("acks", "all");
		props.put("retries", 0);
		props.put("batch.size", 16384);
		props.put("linger.ms", 1);
		props.put("buffer.memory", 33554432);
		props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
		props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");		

		// Topics.
		Collection<String> topics = Arrays.asList(input_topic);

		// Streams.
		final JavaInputDStream<ConsumerRecord<String,String>> stream =
				KafkaUtils.createDirectStream(
						jssc,
						LocationStrategies.PreferConsistent(),
						ConsumerStrategies.<String, String>Subscribe(topics, kafkaParams)
						);		

		// Processing.		

		// Step 1. Current Time.
		JavaDStream<String> lines = stream.map(ConsumerRecord::value);		
		JavaDStream<String> timeLines = lines.map(line -> line + "," + System.currentTimeMillis());

		// Step 2. Filtering for String
		JavaDStream<String> filtered = timeLines.filter( sample -> {
			
			String[] commands = sample.split(",");				
			String[] words = commands[0].split(" ");			
			BloomFilter temp = bloom_filter.value();
			
			
			for ( String word: words ) {
				if (temp.filtering(word)) {
					return true;
				}
			}						
			return false;
		});

		// Step 3. Out > Kafka, Redis
		filtered.foreachRDD( samples -> {

			samples.foreach( sample -> {

				pool = new JedisPool(new JedisPoolConfig(), "192.168.56.100");
				Jedis jedis = pool.getResource();
				jedis.select(0);

				KafkaProducer<String, String> producer = new KafkaProducer<String, String>(props);
				
				//System.out.println(sample);
				
				String[] commands = sample.split(",");
				String value = commands[0];
				int index = Integer.parseInt(commands[1]);

				String out = value + "," + index + "," + commands[2] + "," + commands[3] + "," + System.currentTimeMillis();
				jedis.rpush(redis_key, out);				
				producer.send(new ProducerRecord<String, String>(output_topic, out));				
					
				jedis.close();
			});				
		});	

		// Start.
		jssc.start();
		jssc.awaitTermination();		
		//producer.close();
		//pool.close();		
	}		
}
