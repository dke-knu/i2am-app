package i2am.Sampling;

import org.apache.storm.redis.common.config.JedisClusterConfig;
import org.apache.storm.redis.common.container.JedisCommandsContainerBuilder;
import org.apache.storm.redis.common.container.JedisCommandsInstanceContainer;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.topology.base.BaseRichBolt;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.JedisCommands;

import java.util.Iterator;
import java.util.Map;
import java.util.PriorityQueue;


public class UCKSampleBolt extends BaseRichBolt{
    private long count;
    private int windowSize;
    private int  samplingRate;
    private double ucUnderBound;
    PriorityQueue<SampleElement> sample = new PriorityQueue<SampleElement>();

    /* RedisKey */
    private String redisKey = null;
    private String srKey = "SamplingRate";
    private String ucKey = "UCUnderBound";

    /* Jedis */
    private JedisCommandsInstanceContainer jedisContainer = null;
    private JedisClusterConfig jedisClusterConfig = null;
    private JedisCommands jedisCommands = null;

    private OutputCollector collector;

    /* Logger */
    private final static Logger logger = LoggerFactory.getLogger(UCKSampleBolt.class);

    public UCKSampleBolt(String redisKey, JedisClusterConfig jedisClusterConfig){
        this.count = 0;
        this.redisKey = redisKey;
        this.jedisClusterConfig = jedisClusterConfig;
    }

    @Override
    public void prepare(Map map, TopologyContext topologyContext, OutputCollector collector) {
        this.collector = collector;

        if (jedisClusterConfig != null) {
            this.jedisContainer = JedisCommandsContainerBuilder.build(jedisClusterConfig);
            jedisCommands = jedisContainer.getInstance();
        } else {
            throw new IllegalArgumentException("Jedis configuration not found");
        }

        logger.info("############# UCKSAMPLEBOLT");
        logger.info(jedisCommands.hget(redisKey, srKey));

		/* Get parameters */
        samplingRate = Integer.parseInt(jedisCommands.hget(redisKey, srKey));
        ucUnderBound = Double.parseDouble(jedisCommands.hget(redisKey, ucKey));
        windowSize = window_calculator();
    }

    @Override
    public void execute(Tuple input) {
        double rand;
        int wLength;
        int sLength;
        String element = input.getString(0);
        count++;

        if((count%samplingRate)==0) sLength=samplingRate;
        else sLength=(int)count%samplingRate;
        wLength=(int)count%windowSize;

        rand = Math.random();

        if(sLength==1){
            //sample increase
            sample.add(new SampleElement(element, rand));  //current data insert
        }
        else{
            //current slot sampling
            SampleElement tmp = sample.peek();
            if (rand > tmp.getRand() ) {
                sample.poll();
                sample.add(new SampleElement(element,rand));
            }
        }

        if (wLength==0){
            Iterator<SampleElement> iterator = sample.iterator();

            while(iterator.hasNext()){
                collector.emit(new Values(iterator.next().getElement())); //emit
            }
        }
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declare(new Fields("data"));
    }

    public int window_calculator(){
        int size=100;

        return size;
    }
}

class SampleElement{
    private String data;
    private double rand;

    public SampleElement(String data, double rand){
        this.data=data;
        this.rand=rand;
    }

    public String getElement() { return data; }

    public double getRand() { return rand; }
}