package knu.cs.dke.topology_manager_v3.topolgoies;

public class ReservoirSamplingTopology extends ASamplingFilteringTopology {

	private int sampleSize;
	private int windowSize;
	
	public ReservoirSamplingTopology(String createdTime, String plan, int index, String topologyType, int sampleSize, int windowSize) {

		super(createdTime, plan, index, topologyType);
		this.sampleSize = sampleSize;
		this.windowSize = windowSize;				
	}	

	public int getWindowSize() {
		return windowSize;
	}

	public void setWindowSize(int windowSize) {
		this.windowSize = windowSize;
	}

	public int getSampleSize() {
		return sampleSize;
	}

	public void setSampleSize(int sampleSize) {
		this.sampleSize = sampleSize;
	}

	@Override
	public void killTopology() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void avtivateTopology() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void deactivateTopology() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void submitTopology() {
		// TODO Auto-generated method stub
		
	}

}
