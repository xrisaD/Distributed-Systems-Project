public class ArtistName {

	private String artistName;
	
	//constructor
	public ArtistName(String artistName){
		this.artistName=artistName;
	}
	//setters and getters
	public String getArtistName() {
		return artistName;
	}
	public void setArtistName(String artistName) {
		this.artistName = artistName;
	}

	@Override
	/**
	 * Need this method for hash maps
	 */
	public int hashCode(){
		return artistName.hashCode();
	}
}