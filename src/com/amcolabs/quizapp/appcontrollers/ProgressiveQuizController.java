package com.amcolabs.quizapp.appcontrollers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

import android.os.Handler;

import com.amcolabs.quizapp.AppController;
import com.amcolabs.quizapp.QuizApp;
import com.amcolabs.quizapp.User;
import com.amcolabs.quizapp.databaseutils.Question;
import com.amcolabs.quizapp.databaseutils.Quiz;
import com.amcolabs.quizapp.screens.ClashScreen;
import com.amcolabs.quizapp.screens.QuestionScreen;
import com.amcolabs.quizapp.serverutils.ServerResponse;
import com.amcolabs.quizapp.serverutils.ServerResponse.MessageType;
import com.amcolabs.quizapp.serverutils.ServerWebSocketConnection;
import com.amcolabs.quizapp.uiutils.UiUtils.UiText;
import com.google.android.gms.wallet.Payments;
import com.google.gson.JsonSyntaxException;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;

public class ProgressiveQuizController extends AppController{
	User user;
	User user2;
	
	private ServerWebSocketConnection serverSocket;
	
	private Quiz quiz;
		

	
	public ProgressiveQuizController(QuizApp quizApp) {
		super(quizApp);
	}

	
	public void initlializeQuiz(Quiz quiz) {
		this.quiz = quiz;
//		QuestionScreen questionScreen = new QuestionScreen(this);
//		insertScreen(questionScreen);
		showWaitingScreen(quiz);
	}

	ClashScreen clashingScreen = null;
	QuestionScreen questionScreen = null;
	public void showWaitingScreen(Quiz quiz){
		clearScreen();
		clashingScreen = new ClashScreen(this);
		clashingScreen.setClashCount(2);
		clashingScreen.updateClashScreen(quizApp.getUser()/*quizApp.getUser()*/, 0);//TODO: change to quizApp.getUser()
		insertScreen(clashingScreen);
		quizApp.getServerCalls().startProgressiveQuiz(this, quiz);
	}	
	
	
	public void showQuestionScreen(ArrayList<User> users){
		for(User user: currentUsers){
			int index = 0;
			if(user.uid!=quizApp.getUser().uid){
				try{
					clashingScreen.updateClashScreen(user, ++index);
				}
				catch(NullPointerException e){
					e.printStackTrace();
				}
			}
		}
		//pre download assets if ever its possible
		questionScreen = new QuestionScreen(this);
		questionScreen.showUserInfo(users);
		//animate TODO:
		new Handler().postDelayed(new Runnable() {
			
			@Override
			public void run() {
				clearScreen();
				clashingScreen = null; // dispose of it 
				insertScreen(questionScreen);
			}
		}, 2000);
		
		new Handler().postDelayed(new Runnable() {
			
			@Override
			public void run() {
		        userAnswers = new HashMap<String , UserAnswer>();
		        userAnswersStack.clear();
		        currentScore = 0;
		        
				questionScreen.animateQuestionChange(UiText.GET_READY.getValue(), UiText.FOR_YOUR_FIRST_QUESTION.getValue() ,currentQuestions.remove(0));
			}
		}, 4000);
	}
	
	@Override
	public void onDestroy() {
	}
	
	int backPressedCount = 0;
	
	@Override
	public boolean onBackPressed() {
		if(quizApp.peekCurrentScreen() instanceof QuestionScreen){
			backPressedCount++;
			if(backPressedCount>1){
				backPressedCount = 0;
				gracefullyCloseSocket();
				return false;
			}
			return true;
		}
		
		else{
			return false;
		}
	}
/*
 * Current running quiz
 */
	
	public final  static String QUESTIONS = "1";
	public final  static String CURRENT_QUESTION = "2";
	public final  static String MESSAGE_TYPE = "3";
	public final static String QUESTION_ID = "4";
	public final  static String WHAT_USER_HAS_GOT = "5";
	public final  static String N_CURRENT_QUESTION_ANSWERED = "6";
	public final  static String USER_ANSWER = "7";
	public final static  String USERS="8";
	public final  static String CREATED_AT="9";
	public final  static String ELAPSED_TIME="10"; 
	
	double waitinStartTime = 0;
	boolean noResponseFromServer = true;
	String serverId = null;
	private boolean botMode = false;
	
	private ArrayList<Question> currentQuestions = new ArrayList<Question>();
	private ArrayList<User> currentUsers = new ArrayList<User>();
	private HashMap<String ,UserAnswer> userAnswers;
	private HashMap<String ,ArrayList<UserAnswer>> userAnswersStack = new HashMap<String, ArrayList<UserAnswer>>();
	
	private int currentScore = 0;
	private int botScore =0;
	protected Random rand = new Random();
	
	private  void setBotMode(boolean mode){
		botMode = mode;
		botScore = 0;
	}
	private boolean isBotMode(){
		return botMode;
	}
	
	private String constructSocketMessage(MessageType messageType , HashMap<String, String> data , HashMap<Integer, String> data1){
		String jsonStr = "{\""+MESSAGE_TYPE+"\":"+Integer.toString(messageType.getValue())+",";
		if(data!=null){
			for(String key:data.keySet()){
				jsonStr+="\""+key+"\":\""+data.get(key)+"\",";
			}
		}
		if(data1!=null){
			for(int key:data1.keySet()){
				jsonStr+="\""+Integer.toString(key)+"\":\""+data.get(key)+"\",";
			}
		}
		jsonStr = jsonStr.substring(0, jsonStr.length()-1); //remove a ,
		return jsonStr+"}";
	}


	public void startSocketConnection(ServerWebSocketConnection mConnection, final Quiz quiz) {
		serverSocket = mConnection;
		waitinStartTime = quizApp.getConfig().getCurrentTimeStamp();

		new Handler().postDelayed(new Runnable() {
			@Override
			public void run() {
				if(noResponseFromServer){
					serverSocket.sendTextMessage(constructSocketMessage(MessageType.ACTIVATE_BOT, null, null));
				}
			}
		}, 5000);
	}
	
	static class UserAnswer{
		@SerializedName(MESSAGE_TYPE)
		int messageType = MessageType.USER_ANSWERED_QUESTION.getValue();
	    @SerializedName(QUESTION_ID)
		String questionId;
	    @SerializedName("uid")
		String uid;
	    @SerializedName(USER_ANSWER)
		String userAnswer;
	    @SerializedName(ELAPSED_TIME)
		int elapsedTime;
	    @SerializedName(WHAT_USER_HAS_GOT)
		int whatUserGot;
		
		public UserAnswer(String questionId, String uid, String userAnswer, int elapsedTime, int whatUserGot) {
			this.questionId = questionId;
			this.uid = uid;
			this.userAnswer = userAnswer;
			this.elapsedTime = elapsedTime;
			this.whatUserGot = whatUserGot;
		}
	}
	
	private void checkAndProceedToNextQuestion(UserAnswer userAnswer){
		userAnswers.put(userAnswer.uid,  userAnswer);
		if(userAnswersStack.containsKey(userAnswer.uid)){
			userAnswersStack.get(userAnswer.uid).add(userAnswer);
		}
		if(currentUsers.size() == userAnswers.keySet().size()){//every one answered 
			for(String u: userAnswers.keySet()){
				questionScreen.animateXpPoints(u, userAnswers.get(u).whatUserGot); 
			}
			if(currentQuestions.size()>0){
				new Handler().postDelayed(new Runnable() {
					
					@Override
					public void run() {
							Question currentQuestion = currentQuestions.remove(0);
							questionScreen.animateQuestionChange(UiText.QUESTION.getValue(currentQuestions), UiText.GET_READY.getValue(), currentQuestion);
							for(User user:currentUsers){ // check for any bots and schedule
								if(!user.isBotUser()) continue;
								
								int elapsedTime = rand.nextInt(10*Math.max(0, (100-quizApp.getUser().getLevel(quiz))/100)); 
								boolean isRightAnswer = rand.nextInt(2)==1? false:true;
								if(isRightAnswer){
									botScore+=Math.ceil(currentQuestion.getTime() - elapsedTime)*multiplyFactor();
								}
								final UserAnswer botAnswer = new UserAnswer(currentQuestion.questionId, user.uid, isRightAnswer?currentQuestion.getCorrectAnswer():currentQuestion.getWrongRandomAnswer(rand),
										 	elapsedTime, botScore);
								
								new Handler().postDelayed( new Runnable() {
									
									@Override
									public void run() {
										checkAndProceedToNextQuestion(botAnswer);
									}
								}, 2000+elapsedTime*1000);
							}
					}
				}, 1500);
			}
			else if(isBotMode()){
				new Handler().postDelayed(new Runnable() {
					@Override
					public void run() {
						validateAndShowWinningScreen();
					}
				}, 2000);
			}
		}
	}
	
	public void validateAndShowWinningScreen(){
		ArrayList<UserAnswer> l = userAnswersStack.get(quizApp.getUser().uid);
		System.out.println(l.toString());
	}
	
	public void onMessageRecieved(MessageType messageType, ServerResponse response, String data) {
		switch(messageType){
	    	case USER_ANSWERED_QUESTION:
	    		UserAnswer userAnswer = quizApp.getConfig().getGson().fromJson(response.payload, UserAnswer.class);
	    		//questionId , self.uid, userAnswer,elapsedTime , whatUserGot
	    		questionScreen.getTimerView().stopPressed((int)userAnswer.elapsedTime);
	    		checkAndProceedToNextQuestion(userAnswer);
	    		break;
	    	case GET_NEXT_QUESTION://client trigger
	    		break; 
	    	case STARTING_QUESTIONS:// start questions // user finalised
	    		noResponseFromServer = false;
	    		currentUsers = quizApp.getConfig().getGson().fromJson(response.payload1,new TypeToken<ArrayList<User>>(){}.getType());
	    		currentQuestions  = quizApp.getConfig().getGson().fromJson(response.payload2,new TypeToken<ArrayList<Question>>(){}.getType());
	    		showQuestionScreen(currentUsers);
	    		break;
	    	case ANNOUNCING_WINNER:
	    		validateAndShowWinningScreen();
	    		break; 
	    	case USER_DISCONNECTED:
	    		break; 
	    	case NEXT_QUESTION:
	    		break; 
	    	case START_QUESTIONS:
	    		break; 
	    	case STATUS_WHAT_USER_GOT:
	    		break; 
	    	case OK_ACTIVATING_BOT: 
	    		quizApp.getServerCalls().informActivatingBot(quiz, serverSocket.serverId); 
	    		currentQuestions = quizApp.getConfig().getGson().fromJson(response.payload1, new TypeToken<List<Question>>(){}.getType());
	    		try{
	    			currentUsers = quizApp.getConfig().getGson().fromJson(response.payload, new TypeToken<List<User>>(){}.getType());
	    		}
	    		catch(JsonSyntaxException ex){
	    			currentUsers.add(quizApp.getUser());
	    			currentUsers.add((User) quizApp.getConfig().getGson().fromJson(response.payload, new TypeToken<User>(){}.getType()));
	    		}
	    		setBotMode(true);
	    		serverSocket.disconnect();
	    		showQuestionScreen(currentUsers);
	    		break;
			default:
				break;
		}
	}


	public void onSocketClosed() {
		//TODO: poup
	}
	
	public void gracefullyCloseSocket(){
		if(serverSocket!=null){
			serverSocket.disconnect();
		}
	}


	public void ohNoDammit() {
	}

	private int multiplyFactor(){
		if(currentQuestions.size()%4==0 && currentQuestions.size()<quiz.nQuestions){
			return 2;
		};
		return 1;
	}
	
	public void onOptionSelected(Boolean isAnwer, String answer , double timeElapsed, Question currentQuestion) {
		UserAnswer payload =null; 
		currentScore += ( Math.ceil(currentQuestion.getTime()-timeElapsed)* multiplyFactor());
		payload = new UserAnswer(currentQuestion.questionId, quizApp.getUser().uid, answer, (int)timeElapsed, currentScore);
		if(!isBotMode())
			serverSocket.sendTextMessage(quizApp.getConfig().getGson().toJson(payload));
		checkAndProceedToNextQuestion(payload);
	}
}

