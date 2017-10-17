import java.sql.*;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import edu.stanford.nlp.tagger.maxent.MaxentTagger;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Scanner;
import java.io.BufferedReader;
import java.util.HashMap;
import java.util.Set;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;

public class Preprocess implements ActionListener{
	
	private static String pathToSWN = "SentiWordNet_3.0.0_20130122.txt";
	private final static String emo_regex = "([\\u20a0-\\u32ff\\ud83c\\udc00-\\ud83d\\udeff\\udbb9\\udce5-\\udbb9\\udcee])";
    private HashMap<String, Double> _dict;
	
	private Scanner in;
	private BufferedReader csv;
	
	//WindowBuilder Variables
	private JPanel panel;
	private JFrame frame;
	private JTextField textField;
	private JTable table;
	private JRadioButton rdbtnCamera,rdbtnMobile,rdbtnLaptop,rdbtnTablet;
	private DefaultTableModel model;
	private int product=1;
	private String filename;
	private double[] counts=new double[6]; 
	
	public Preprocess(){
		in = new Scanner (System.in);
        _dict = new HashMap<String, Double>();
        HashMap<String, Vector<Double>> _temp = new HashMap<String, Vector<Double>>();
        try{
            csv = new BufferedReader(new FileReader(pathToSWN));
            String line = "";           
            while((line = csv.readLine()) != null)
            {
                String[] data = line.split("\t");
                Double score = Double.parseDouble(data[2])-Double.parseDouble(data[3]);
                String[] words = data[4].split(" ");
                for(String w:words)
                {
                    String[] w_n = w.split("#");
                    w_n[0] += "#"+data[0];
                    int index = Integer.parseInt(w_n[1])-1;
                    if(_temp.containsKey(w_n[0]))
                    {
                        Vector<Double> v = _temp.get(w_n[0]);
                        if(index>v.size())
                            for(int i = v.size();i<index; i++)
                                v.add(0.0);
                        v.add(index, score);
                        _temp.put(w_n[0], v);
                    }
                    else
                    {
                        Vector<Double> v = new Vector<Double>();
                        for(int i = 0;i<index; i++)
                            v.add(0.0);
                        v.add(index, score);
                        _temp.put(w_n[0], v);
                    }
                }
            }
            Set<String> temp = _temp.keySet();
            for (Iterator<String> iterator = temp.iterator(); iterator.hasNext();) {
                String word = iterator.next();
                Vector<Double> v = _temp.get(word);
                double score = 0.0;
                double sum = 0.0;
                for(int i = 0; i < v.size(); i++)
                    score += ((double)1/(double)(i+1))*v.get(i);
                for(int i = 1; i<=v.size(); i++)
                    sum += (double)1/(double)i;
                score /= sum;
                @SuppressWarnings("unused")
				String sent = "";               
                if(score>=0.75)
                    sent = "strong_positive";
                else
                if(score > 0.25 && score<=0.5)
                    sent = "positive";
                else
                if(score > 0 && score>=0.25)
                    sent = "weak_positive";
                else
                if(score < 0 && score>=-0.25)
                    sent = "weak_negative";
                else
                if(score < -0.25 && score>=-0.5)
                    sent = "negative";
                else
                if(score<=-0.75)
                    sent = "strong_negative";
                _dict.put(word, score);
            }
        }
        catch(Exception e){e.printStackTrace();}
	}

	public void displayTable(){
		Connection c = null;
	    Statement stmt = null;
	    try {
	      Class.forName("org.sqlite.JDBC");
	      c = DriverManager.getConnection("jdbc:sqlite:project.db");
	      c.setAutoCommit(false);

	      stmt = c.createStatement();
	      ResultSet rs = stmt.executeQuery( "SELECT * FROM REVIEWS;" );
	      while ( rs.next() ) {
	         int id = rs.getInt("REVIEW_ID");
	         int  pro_id = rs.getInt("PRODUCT_ID");
	         String title  = rs.getString("TITLE");
	         String  content = rs.getString("CONTENT");
	         double rating[]=new double[6];
	         rating=sentenceSplitter(content);
	         System.out.println( "REVIEW ID : " + id +"\nPRODUCT ID : " + pro_id );
	         System.out.println("TITLE : " + title +"\nCONTENT : " + content);
	         for(int i=0;i<6;i++){
	        	 System.out.println("Aspect "+i+" = "+rating[i]);
	         }
	         
	      }
	      rs.close();
	      stmt.close();
	      c.close();
	    } catch ( Exception e ) {
	      System.err.println( e.getClass().getName() + ": " + e.getMessage() );
	      System.exit(0);
	    }
	    System.out.println("Operation done successfully");
	}
	
	public void createTable(){
		Connection c = null;
	    Statement stmt = null;
		try {
			Class.forName("org.sqlite.JDBC");
			c = DriverManager.getConnection("jdbc:sqlite:project.db");
			System.out.println("Opened database successfully");

			stmt = c.createStatement();
			String sql = "CREATE TABLE REVIEWS " +
	                   "(REVIEW_ID INT  PRIMARY KEY   NOT NULL," + 
	                   " PRODUCT_ID           INT  NOT NULL, " + 
	                   " TITLE        TEXT, " + 
	                   " CONTENT         TEXT)";
			stmt.executeUpdate(sql);
			stmt.close();
			c.close();
		} catch ( Exception e ) {
			System.err.println( e.getClass().getName() + ": " + e.getMessage() );
			System.exit(0);
	    }
	    System.out.println("Table created successfully");
	}
	
	public void insertReviews(){
		JSONParser parser = new JSONParser();
		Connection c = null;
		Statement stmt = null;
		try {
        	Class.forName("org.sqlite.JDBC");
            c = DriverManager.getConnection("jdbc:sqlite:project.db");
            c.setAutoCommit(false);
            System.out.println("Opened database successfully");

            Object obj = parser.parse(new FileReader("B000A7JKMW.json"));

            JSONObject jsonObject = (JSONObject) obj;
            System.out.println(jsonObject);
            
            JSONArray Reviews= (JSONArray) jsonObject.get("Reviews");
            
            for(int i=0; i<Reviews.size(); i++){
            	System.out.println("The " + i + " element of the array: "+Reviews.get(i));
            }
            @SuppressWarnings("rawtypes")
			Iterator i = Reviews.iterator();
            stmt = c.createStatement();
            
            int reviewCounter=1;
            
            while (i.hasNext()) {
            	
            	JSONObject innerObj = (JSONObject) i.next();
            	System.out.println("Product: Canon EOS Rebel T3 1100D Guide to Digital SLR\nTitle: "
            	+ innerObj.get("Title") +"\nContent: " + innerObj.get("Content")+"\n");
            	String title=innerObj.get("Title").toString();
            	for(int i1=-1;(i1=title.indexOf("'",i1+1))!=-1;){
            		title=new StringBuilder(title).insert(i1, "'").toString();
            		i1++;
            	}
            	String content=innerObj.get("Content").toString();
            	for(int i1=-1;(i1=content.indexOf("'",i1+1))!=-1;){
            		content=new StringBuilder(content).insert(i1, "'").toString();
            		i1++;
            	}
            	
            	String sql = "INSERT INTO REVIEWS (REVIEW_ID,PRODUCT_ID,TITLE,CONTENT) " +
                        "VALUES ( "+reviewCounter+",1, '"+title+"', '"+content+"');";
            	stmt.executeUpdate(sql);
            	reviewCounter++;
            }

            stmt.close();
            c.commit();
            c.close();

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ParseException e) {
            e.printStackTrace();
        } catch (Exception e ) {
            System.err.println( e.getClass().getName() + ": " + e.getMessage() );
            System.exit(0);
        }
	}
	
	public double[] sentenceSplitter(String input){
		int len,i,j;
		double[] ratings= new double[6];
		double[] totalRatings=new double[6];
		float emojiScore=emojiDetection(input);
		input = input.toLowerCase();
		String[] sentence=input.split("\\.");
		len=sentence.length;
		for(i=0;i<len;i++){
			if(sentence[i].length() <6){
				continue;
			}
			if(product==1){
				ratings=mobileAspectDetection(sentence[i]);
			}else if(product==2){
				ratings=cameraAspectDetection(sentence[i]);}
			else if(product==3){
				ratings=laptopAspectDetection(sentence[i]);
			}else if(product==4){
				ratings=tabletAspectDetection(sentence[i]);
			}
			for(j=0;j<6;j++){
				if(ratings[j]!=0){
					totalRatings[j]=(ratings[j]+totalRatings[j]);
				}
			}
		}
		if(emojiScore!=0){
			totalRatings[5]=totalRatings[5]*.75+emojiScore*.25;
		}
		for(i=0;i<6;i++){
			totalRatings[i]=Math.min(totalRatings[i]/counts[i],2.0);
			totalRatings[i]=Math.max(-2.0,totalRatings[i]);
		}
		return totalRatings;
	}
	
	public double[] mobileAspectDetection(String input){
		double[] aspects=new double[6];
		List<String>storage=Arrays.asList("battery","charge","memory","card","storage","space");
		List<String>performance=Arrays.asList("hardware","signal","cell","phone","device","processor","speed","performance","reception","network","functionality","option");
		List<String>display=Arrays.asList("feel","look","design","display","resolution","pictures","picture","screen");
		List<String>features=Arrays.asList("sound","camera","features","security","protection","video","speaker","noise");
		List<String>cost=Arrays.asList("price","affordable","expensive","value","cheap","worth","money","inexpensive","purchase");
		String word1;
		int count=0;
		input = input.replaceAll("([^a-zA-Z\\s])", "");
		MaxentTagger tagger = new MaxentTagger("taggers/english-left3words-distsim.tagger");
	    String tagged = tagger.tagString(input);
	    
		String[] parts=input.trim().split("\\s+");
		double temp=rater(tagged);
		while (count<parts.length) {
			word1 = parts[count];
		    if (storage.contains(word1)) {
		    	aspects[0]+=temp;
		    	counts[0]++;
		    }
		    if (performance.contains(word1)) {
		        aspects[1]+=temp;
		        counts[1]++;
		    }
		    if (display.contains(word1)) {
		    	aspects[2]+=temp;
		    	counts[2]++;
		    }
		    if (features.contains(word1)) {
		        aspects[3]+=temp;
		        counts[3]++;
		    }
		    if (cost.contains(word1)) {
		        aspects[4]+=temp;
		        counts[4]++;
		    }		    
		    count++;
		}
		aspects[5]+=temp;  //overall calculation
	    counts[5]++;
	    
		return aspects;
	}
	
	public double[] cameraAspectDetection(String input){
		double[] aspects=new double[6];
		List<String>storage=Arrays.asList("battery","charge","rechargable","power","memory","card","storage");
		List<String>performance=Arrays.asList("interface","display","camera","use","usability","flexibility","speed","performance","antishake","autofocus","flash","zoom");
		List<String>image=Arrays.asList("light","capture","results","shots","images","image","pictures","picture","quality");
		List<String>cost=Arrays.asList("price","affordable","expensive","value","cheap","worth","money","inexpensive","purchase");
		List<String>service=Arrays.asList("support","warranty","firmware","optics","lenses");
		String word1;
		int count=0;
		
		input = input.replaceAll("([^a-zA-Z\\s])", "");
		MaxentTagger tagger = new MaxentTagger("taggers/english-left3words-distsim.tagger");
	    String tagged = tagger.tagString(input);
	    
		String[] parts=input.trim().split("\\s+");
		double temp=rater(tagged);
		while (count<parts.length) {
			word1 = parts[count];
		    if (storage.contains(word1)) {
		    	aspects[0]+=temp;
		    	counts[0]++;
		    }
		    if (performance.contains(word1)) {
		        aspects[1]+=temp;
		        counts[1]++;
		    }
		    if (image.contains(word1)) {
		    	aspects[2]+=temp;
		    	counts[2]++;
		    }
		    if (cost.contains(word1)) {
		        aspects[3]+=temp;
		        counts[3]++;
		    }
		    if (service.contains(word1)) {
		        aspects[4]+=temp;
		        counts[4]++;
		    }
		    count++;
		}
		
		aspects[5]+=temp;  //overall calculation
		counts[5]++;
		
		return aspects;
	}
	
	public double[] laptopAspectDetection(String input){
		double[] aspects=new double[6];
		List<String>battery=Arrays.asList("battery","charge");
		List<String>performance=Arrays.asList("machine","motherboard","system","flexibility","processor","speed","performance","laptop","computer","pc","device","product");
		List<String>output=Arrays.asList("video","display","resolution","pictures","picture","screen","sound");
		List<String>features=Arrays.asList("features","wifi","usb","internet","keyboard","program","memory","drives","storage");
		List<String>service=Arrays.asList("service","delivery","company","buisness","function","assistance","cost","price");
		String word1;
		int count=0;
		
		input = input.replaceAll("([^a-zA-Z\\s])", "");
		MaxentTagger tagger = new MaxentTagger("taggers/english-left3words-distsim.tagger");
	    String tagged = tagger.tagString(input);
	    
		String[] parts=input.trim().split("\\s+");
		double temp=rater(tagged);
		while (count<parts.length) {
			word1 = parts[count];
		    if (battery.contains(word1)) {
		    	aspects[0]+=temp;
		    	counts[0]++;
		    }
		    if (performance.contains(word1)) {
		        aspects[1]+=temp;
		        counts[1]++;
		    }
		    if (output.contains(word1)) {
		    	aspects[2]+=temp;
		    	counts[2]++;
		    }
		    if (features.contains(word1)) {
		        aspects[3]+=temp;
		        counts[3]++;
		    }
		    if (service.contains(word1)) {
		        aspects[4]+=temp;
		        counts[4]++;
		    }
		    count++;
		}
		
		aspects[5]+=temp;  //overall calculation
		counts[5]++;
		
		return aspects;
	}
	
	public double[] tabletAspectDetection(String input){
		double[] aspects=new double[6];
		List<String>connectivity=Arrays.asList("wifi","3g","lte","network","bluetooth");
		List<String>performance=Arrays.asList("phablet","cell","phone","pad","product","devices","tablet","tab","speed","performance","app","hardware");
		List<String>lookNfeel=Arrays.asList("weight","size","color","display","screen","images","image","pictures","picture","quality");
		List<String>features=Arrays.asList("aspects","features","touch","keyboard","system","security","sound","sim","portable","internet","safe","services","grip","response","speaker");
		List<String>storage=Arrays.asList("battery","charge","memory","card","storage","functional");
		String word1;
		int count=0;
		
		input = input.replaceAll("([^a-zA-Z\\s])", "");
		MaxentTagger tagger = new MaxentTagger("taggers/english-left3words-distsim.tagger");
	    String tagged = tagger.tagString(input);
	    
		String[] parts=input.trim().split("\\s+");
		double temp=rater(tagged);
		while (count<parts.length) {
			word1 = parts[count];
		    if (connectivity.contains(word1)) {
		    	aspects[0]+=temp;
		    	counts[0]++;
		    	
		    }
		    if (performance.contains(word1)) {
		        aspects[1]+=temp;
		        counts[1]++;
		    }
		    if (lookNfeel.contains(word1)) {
		    	aspects[2]+=temp;
		    	counts[2]++;
		    }
		    if (features.contains(word1)) {
		        aspects[3]+=temp;
		        counts[3]++;
		    }
		    if (storage.contains(word1)) {
		        aspects[4]+=temp;
		        counts[4]++;
		    }
		    count++;
		}
		
		aspects[5]+=temp;  //overall calculation
		counts[5]++;
		
		return aspects;
	}
	
	public Double extract(String word)
	{
	    Double total = new Double(0);
	    total = _dict.get(word);
	    if(total!=null)
	    	return total;
	    else
	    	return 0.0;
	}

	public double rater(String tagged){

		Preprocess wordNetObj = new Preprocess();
	    String[] words = tagged.split("\\s+");
	    double totalScore = 0, tempScore =0, scale=.35;

	    for(int i=words.length-1;i>=0;i--) {
	    	String word = words[i];
	    	if(word.contains("#")){
	    		continue;
	    	}
			String prevSecondWord = null;
			String prevWord = null;
			String prevThirdWord = null;
			String prevSecondWordTag = null;
			String prevThirdWordTag = null;
			String prevWordTag = null;
	    		
		    String wordTag = myTagger(word);
		    word=word.split("_",2)[0]+wordTag;
		    words[i]= word;
	    	word.replaceAll("_([A-Z]*)\\b","");

	    	if(i>0){
	    		prevWord = words[i-1];
	    		prevWordTag = myTagger (prevWord);
	    		prevWord=prevWord.split("_",2)[0]+prevWordTag;
	    		if(i>1){
	    			prevSecondWord = words [i-1];
	    			prevSecondWordTag = myTagger (prevSecondWord);
	    			prevSecondWord=prevSecondWord.split("_",2)[0]+prevSecondWordTag;
	    			if(i>2){
	    	    		prevThirdWord = words [i-1];
	    	    		prevThirdWordTag = myTagger (prevThirdWord);
	    	    		prevSecondWord=prevSecondWord.split("_",2)[0]+prevSecondWordTag;
	    	    		}
	    		}
	    	}

	    	if (wordTag == "#a"){
				if(prevWordTag == "#r"){
					if(prevWord == "never"|| prevWord == "not" || prevWord == "no"){
						tempScore = - wordNetObj.extract(word);
					}else{
						tempScore += wordNetObj.extract(word)+ scale* wordNetObj.extract(prevWord);
					}
					words[i-1]= prevWord;
				}else if(prevWordTag == "#a"){
					if(prevSecondWordTag == "#r"){
						if(prevSecondWord == "never"|| prevSecondWord == "not" || prevSecondWord == "no"){
							tempScore = - wordNetObj.extract(word);
						}else{
							tempScore += wordNetObj.extract(prevWord)+ scale* wordNetObj.extract(prevSecondWord);
						}
						words[i-2]= prevSecondWord;
					}
					tempScore += wordNetObj.extract(word);
					words[i-1]= prevWord;
				}else if(prevWordTag == "#c"){
					tempScore += wordNetObj.extract(word);
					words[i-1]= prevWord;
				}else if(prevSecondWordTag == "#r"){
					if(prevSecondWord == "never"|| prevSecondWord == "not" || prevSecondWord == "no"){
						tempScore = - wordNetObj.extract(word);
					}else{
						tempScore += wordNetObj.extract(word)+ scale* wordNetObj.extract(prevSecondWord);
					}
					words[i-2]= prevSecondWord;
				}else if(prevSecondWordTag == "#a"){
					if (prevThirdWordTag == "#r"){
						if(prevThirdWord == "never"|| prevThirdWord == "not" || prevThirdWord == "no"){
							tempScore = - wordNetObj.extract(word);
						}else{
							tempScore += scale* wordNetObj.extract(prevThirdWord);
						}
						words[i-3]= prevThirdWord;
					}
					tempScore += wordNetObj.extract(word)+ wordNetObj.extract(prevSecondWord);
					words[i-2]= prevSecondWord;
				}else{
					tempScore+=wordNetObj.extract(word);
				}
			}
			else if (wordTag == "#v"){
				if(prevWordTag == "#r"){
					if(prevWord == "never"|| prevWord == "not" || prevWord == "no"){
						tempScore = - wordNetObj.extract(word);
					}
					words[i-1]= prevWord;
				}else{
					tempScore+=wordNetObj.extract(word);
				}
				
			}
			else if (wordTag == "#n"){
				if(word == "nobody" || word == "noone" || word == "none"){
					totalScore = - totalScore;
				}
				else if (prevWordTag== "#r"){
					if(word == "everybody"||word == "all"||word == "everyone" ){
						if(prevWord == "never"|| prevWord == "not" || prevWord == "no"){
							tempScore = - tempScore;
						}
					}
					words[i-1]= prevWord;
				}
			}else 
				continue;

	        totalScore += tempScore;
	    }
	    totalScore=Math.min(totalScore,2.0);
		totalScore=Math.max(-2.0,totalScore);
		return totalScore;
	}
	
	public String myTagger(String word){
		String tag;
		if(word.contains("#")){
			tag = word.substring(word.length()-2);
			return tag;
		}
		if (word.endsWith("_JJ")||word.endsWith("_JJR")||word.endsWith("_JJS")){
			tag = "#a";
		}
		else if (word.endsWith("_VB")||word.endsWith("_VBG")||word.endsWith("_VBD")||word.endsWith("_VBN")||word.endsWith("_VBP")||word.endsWith("_VBZ")){
			tag = "#v";
		}
		else if (word.endsWith("_RB")||word.endsWith("_RBR")||word.endsWith("_RBS")){
			tag = "#r";
		}
		else if (word.endsWith("_NN")||word.endsWith("_NNP")||word.endsWith("_NNS")||word.endsWith("_NNSP")){
			tag = "#n";
		}else if (word.endsWith("_CC")){
    		tag = "#c";
    	}else {
			tag = null;
		}
		return tag;
	} 
	
	public static void main(String[] args) {
		Preprocess obj=new Preprocess();
		obj.initialize();
		obj.menu();
    }
	private void initialize() {
		frame = new JFrame();
		frame.setTitle("Sentiment Analyzer");
		frame.setBounds(100, 100, 600, 530);
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.getContentPane().setLayout(new BorderLayout(0, 0));
		
		JPanel inputPanel = new JPanel();
		frame.getContentPane().add(inputPanel, BorderLayout.NORTH);
		GridBagLayout gbl_inputPanel = new GridBagLayout();
		gbl_inputPanel.columnWidths = new int[]{104, 86, 68, 63, 59, 0, 0};
		gbl_inputPanel.rowHeights = new int[]{23, 23, 0};
		gbl_inputPanel.columnWeights = new double[]{0.0, 0.0, 0.0, 0.0, 0.0, 0.0, Double.MIN_VALUE};
		gbl_inputPanel.rowWeights = new double[]{0.0, 0.0, Double.MIN_VALUE};
		inputPanel.setLayout(gbl_inputPanel);
		
		JLabel lblInputJsonFilename = new JLabel("Input JSON Filename:");
		GridBagConstraints gbc_lblInputJsonFilename = new GridBagConstraints();
		gbc_lblInputJsonFilename.anchor = GridBagConstraints.WEST;
		gbc_lblInputJsonFilename.insets = new Insets(0, 0, 5, 5);
		gbc_lblInputJsonFilename.gridx = 0;
		gbc_lblInputJsonFilename.gridy = 0;
		inputPanel.add(lblInputJsonFilename, gbc_lblInputJsonFilename);
		
		textField = new JTextField();
		GridBagConstraints gbc_textField = new GridBagConstraints();
		gbc_textField.gridwidth = 2;
		gbc_textField.anchor = GridBagConstraints.WEST;
		gbc_textField.insets = new Insets(0, 0, 5, 5);
		gbc_textField.gridx = 1;
		gbc_textField.gridy = 0;
		inputPanel.add(textField, gbc_textField);
		textField.setColumns(17);
		
		JButton btnStart = new JButton("Start");
		GridBagConstraints gbc_btnStart = new GridBagConstraints();
		gbc_btnStart.insets = new Insets(0, 0, 5, 5);
		gbc_btnStart.gridx = 4;
		gbc_btnStart.gridy = 0;
		inputPanel.add(btnStart, gbc_btnStart);
		btnStart.addActionListener(this);
		
		JLabel lblProductType = new JLabel("Product Type:");
		GridBagConstraints gbc_lblProductType = new GridBagConstraints();
		gbc_lblProductType.anchor = GridBagConstraints.WEST;
		gbc_lblProductType.insets = new Insets(0, 0, 0, 5);
		gbc_lblProductType.gridx = 0;
		gbc_lblProductType.gridy = 1;
		inputPanel.add(lblProductType, gbc_lblProductType);
		
		rdbtnMobile = new JRadioButton("Mobile");
		GridBagConstraints gbc_rdbtnMobile = new GridBagConstraints();
		gbc_rdbtnMobile.anchor = GridBagConstraints.NORTHWEST;
		gbc_rdbtnMobile.insets = new Insets(0, 0, 0, 5);
		gbc_rdbtnMobile.gridx = 1;
		gbc_rdbtnMobile.gridy = 1;
		inputPanel.add(rdbtnMobile, gbc_rdbtnMobile);
		rdbtnMobile.addActionListener(this);
		
		rdbtnCamera = new JRadioButton("Camera");
		GridBagConstraints gbc_rdbtnCamera = new GridBagConstraints();
		gbc_rdbtnCamera.anchor = GridBagConstraints.NORTHWEST;
		gbc_rdbtnCamera.insets = new Insets(0, 0, 0, 5);
		gbc_rdbtnCamera.gridx = 2;
		gbc_rdbtnCamera.gridy = 1;
		inputPanel.add(rdbtnCamera, gbc_rdbtnCamera);
		rdbtnCamera.addActionListener(this);
		
		rdbtnLaptop = new JRadioButton("Laptop");
		GridBagConstraints gbc_rdbtnLaptop = new GridBagConstraints();
		gbc_rdbtnLaptop.insets = new Insets(0, 0, 0, 5);
		gbc_rdbtnLaptop.anchor = GridBagConstraints.NORTHWEST;
		gbc_rdbtnLaptop.gridx = 3;
		gbc_rdbtnLaptop.gridy = 1;
		inputPanel.add(rdbtnLaptop, gbc_rdbtnLaptop);
		rdbtnLaptop.addActionListener(this);
		
		rdbtnTablet = new JRadioButton("Tablet");
		GridBagConstraints gbc_rdbtnTablet = new GridBagConstraints();
		gbc_rdbtnTablet.anchor = GridBagConstraints.WEST;
		gbc_rdbtnTablet.insets = new Insets(0, 0, 0, 5);
		gbc_rdbtnTablet.gridx = 4;
		gbc_rdbtnTablet.gridy = 1;
		inputPanel.add(rdbtnTablet, gbc_rdbtnTablet);
		rdbtnTablet.addActionListener(this);
		
		panel = new JPanel();
		frame.getContentPane().add(panel, BorderLayout.CENTER);
		
		model=new DefaultTableModel();
		panel.setLayout(new FlowLayout(FlowLayout.CENTER, 6, 6));
		table = new JTable(model);
		panel.add(new JScrollPane(table));
		//model.addColumn("Aspect 1");
		//model.addColumn("Aspect 2");
		//model.addColumn("Aspect 3");
		//model.addColumn("Aspect 4");
		//model.addColumn("Aspect 5");
		//model.addColumn("Overall");
		//model.addRow(new Object[]{"v1","v2"});
		
		frame.setVisible(true);
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		if(e.getActionCommand()=="Start"){
			filename=textField.getText();
			if(filename==""||filename==null){
				filename="mobile";
			}
			try{
				model.setColumnCount(0);
				model.setRowCount(0);
				String[][] aspects={{"Storage","Performance","Display","Features","Cost"},
						{"Storage","Performance","Image","Cost","Service"},
						{"Battery","Performance","Output","Features","Service"},
						{"Connectivity","Performance","Interface","Features","Storage"}};
				model.addColumn("Sl. No.");
				model.addColumn(aspects[product-1][0]);
				model.addColumn(aspects[product-1][1]);
				model.addColumn(aspects[product-1][2]);
				model.addColumn(aspects[product-1][3]);
				model.addColumn(aspects[product-1][4]);
				model.addColumn("Overall");
				
				JSONParser parser = new JSONParser();
				Object obj = parser.parse(new FileReader(filename+".json"));
				JSONObject jsonObject = (JSONObject) obj;
				JSONArray Reviews= (JSONArray) jsonObject.get("Reviews");
	            
				@SuppressWarnings("rawtypes")
				Iterator i = Reviews.iterator();
	            int id=1;
				while (i.hasNext()) {
	            	
					JSONObject innerObj = (JSONObject) i.next();
					String title=innerObj.get("Title").toString();
					for(int i1=-1;(i1=title.indexOf("'",i1+1))!=-1;){
						title=new StringBuilder(title).insert(i1, "'").toString();
						i1++;
					}
					String content=innerObj.get("Content").toString();
					for(int i1=-1;(i1=content.indexOf("'",i1+1))!=-1;){
						content=new StringBuilder(content).insert(i1, "'").toString();
						i1++;
					}
					System.out.println(content);
					double a[]=new double[6];
					counts=new double[]{0,0,0,0,0,0};
					a=sentenceSplitter(content);
					model.addRow(new Object[]{id,a[0],a[1],a[2],a[3],a[4],a[5]});
					id++;
	            }

	        } catch (FileNotFoundException ex) {
	            ex.printStackTrace();
	        } catch (Exception ex ) {
	            System.err.println( ex.getClass().getName() + ": " + ex.getMessage() );
	            System.exit(0);
	        }
		}
		if(e.getActionCommand()=="Mobile"){
			if(rdbtnMobile.isSelected()==true){
				product=1;
			}
			rdbtnCamera.setSelected(false);
			rdbtnTablet.setSelected(false);
			rdbtnLaptop.setSelected(false);
		}
		if(e.getActionCommand()=="Camera"){
			if(rdbtnCamera.isSelected()==true){
				product=2;
			}
			rdbtnMobile.setSelected(false);
			rdbtnTablet.setSelected(false);
			rdbtnLaptop.setSelected(false);
		}
		if(e.getActionCommand()=="Laptop"){
			if(rdbtnLaptop.isSelected()==true){
				product=3;
			}
			rdbtnMobile.setSelected(false);
			rdbtnTablet.setSelected(false);
			rdbtnCamera.setSelected(false);
		}
		if(e.getActionCommand()=="Tablet"){
			if(rdbtnTablet.isSelected()==true){
				product=4;
			}
			rdbtnCamera.setSelected(false);
			rdbtnMobile.setSelected(false);
			rdbtnLaptop.setSelected(false);
		}
		
	}

	private void menu(){
		int menu=5;
		while(menu!=0){
			System.out.println("\nEnter 1 to Create Table\nEnter 2 to Insert Reviews\n"
					+ "Enter 3 to Display Table and Analyze\nEnter 0 to exit");
			menu=in.nextInt();
			if(menu==1)
				createTable();
			else if(menu==2)
				insertReviews();
			else if(menu==3)
				displayTable();

		}
	}
	private float emojiDetection(String sentence){
		float score=0;
		int count=0;
		List<String>positiveEmojis=Arrays.asList("😂","😃","😉","😏","😍","😆","😎","❤","👌","👍","✌","👊","💙","💜","💚","💛","💖","💗","💕","💝","🙌","💋","💟","💞");
		List<String>neutralEmojis=Arrays.asList("😐","😔","😣","😞","😅","😷","😶");
		List<String>negativeEmojis=Arrays.asList("😢","☹","😭","😩","😨","😠","💔","🙅","🚫","✖","✋","👎");
		Matcher matcher = Pattern.compile(emo_regex).matcher(sentence);
		while (matcher.find()){
			if(positiveEmojis.contains(matcher.group())){
				score=score+2;
				count++;
			}
			else if(neutralEmojis.contains(matcher.group()))
				count++;
			else if(negativeEmojis.contains(matcher.group())){
				score=score-2;
				count++;
			}
		}
		score=score/count;
		if (Float.isNaN(score))
			return 0;
		else
			return score;
	}
}