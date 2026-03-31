

public class Main {
	
	static String filePath = "test.sgy";
	
	
	
	public static void main(String[] args) {
		
		segyread sr=new segyread();
		sr.read(filePath);
		
}
}
