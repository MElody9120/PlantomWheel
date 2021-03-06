package sample;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ObservableValue;
import javafx.fxml.FXML;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainFormController {
	static StringProperty stepCount = new SimpleStringProperty("0");
	private static StringProperty nowStatus = new SimpleStringProperty("Waiting...");
	
	private Coord stepCache;
	private GraphicsContext mainGC;
	private GraphicsContext captureGC;
	
	private Strategies strategies = new Strategies();
	private History history;
	
	@FXML
	private Label actionLabel;
	
	@FXML
	private Button getStepBtn;
	
	@FXML
	private Button legalBtn;
	
	@FXML
	private Button illegalBtn;
	
	@FXML
	private Button captureBtn;
	
	@FXML
	private Button applyBtn;
	
	@FXML
	private AnchorPane chessboardUI;
	
	@FXML
	private Label stepCountLabel;
	
	@FXML
	private TextArea console;
	
	@FXML
	private ListView<String> historyListView;
	
	@FXML
	private CheckBox reviewCheckBox;
	
	private Coord mousePos = new Coord(0, 0);
	
	@FXML
		//Always get the mouse position
	void getMousePos(MouseEvent event) {
		mousePos = new Coord((int) event.getX(), (int) event.getY());
		if (Main.isAiMode) {
			Main.gameStage.setTitle("PlantomWheel --> " + (Main.isBlackPlayer ? "Black" : "White") + " player" +
					"   --> MousePos: " + mousePos.toNumString());
		} else {
			Main.gameStage.setTitle("PlantomWheel --> " + "Replay" + "   --> MousePos: " + mousePos.toNumString());
		}
	}
	
	@FXML
	void captureRunningOnClick() {
		mainGC.setStroke(Color.RED);
		if (runningCapture.get()) {
			Chess temp = ChessBoard.getChess(mousePosToChessCoord(mousePos));
			if (captureCoords.indexOf(temp.coord) == -1) {
				if (temp.group == null) {
					drawChessBorder(temp.coord, mainGC);
					captureCoords.add(temp.coord);
					
				} else {
					temp.group.chesses.forEach(item -> {
						drawChessBorder(item.coord, mainGC);
						captureCoords.add(item.coord);
					});
				}
			} else {
				if (temp.group == null) {
					drawChessShape(temp.coord, temp.status);
					captureCoords.remove(temp.coord);
				} else {
					temp.group.chesses.forEach(item -> {
						drawChessShape(item.coord, item.status);
						captureCoords.remove(item.coord);
					});
				}
			}
		}
	}
	
	
	@FXML
	void getStepOnClick() {
		nowStatus.set("Thinking...");
		stepCache = strategies.getStep();
		if (stepCache == null) {
			nowStatus.set("Pass！");
			console.appendText("--> Pass!");
			getStepBtn.setDisable(true);
			return;
		}
		drawChessShape(stepCache, 't');
		
		console.appendText("--> Try: " + stepCache.toString() + " : ");
		getStepBtn.setDisable(true);
		captureBtn.setDisable(true);
		legalBtn.setDisable(false);
		illegalBtn.setDisable(false);
		reviewCheckBox.setDisable(true);
		nowStatus.set("Waiting judge...");
	}
	
	
	@FXML
	void legalOnClick() {
		ChessBoard.getChess(stepCache).setChess(Main.isBlackPlayer ? 'b' : 'w');
		drawChessBoard(ChessBoard.board);
		console.appendText("legal.\n");
		history.addStep(stepCache, "legal");
		stepCache = null;
		stepCount.setValue(String.valueOf(Integer.valueOf(stepCount.getValue()) + 1));
		captureBtn.setDisable(false);
		getStepBtn.setDisable(false);
		reviewCheckBox.setDisable(false);
		legalBtn.setDisable(true);
		illegalBtn.setDisable(true);
		nowStatus.set("Waiting...");
	}
	
	@FXML
	void illegalOnClick(MouseEvent event) {
		ChessBoard.getChess(stepCache).setChess('?');
		history.addStep(stepCache, "illegal");
		drawChessBoard(ChessBoard.board);
		console.appendText("illegal.\n");
		getStepOnClick();
	}
	
	
	@FXML
	void captureOnClick() {
		if (!runningCapture.get()) {
			nowStatus.set("Capturing...");
			captureCoords.clear();
			captureBtn.setText("Finish");
			getStepBtn.setDisable(true);
			reviewCheckBox.setDisable(true);
			runningCapture.set(true);
			runCapture();
		} else {
			if (captureCoords.size() > 0) {
				for (int i = 0; i < captureCoords.size(); i++) {
					Chess chess = ChessBoard.getChess(captureCoords.get(i));
					if (chess.group != null) {
						chess.group.chesses.forEach(item -> captureCoords.remove(item.coord));
					}
					chess.capture();
				}
				history.addStep(null, "capture");
			}
			drawChessBoard(ChessBoard.board);
			captureBtn.setText("Capture");
			getStepBtn.setDisable(false);
			runningCapture.set(false);
			reviewCheckBox.setDisable(false);
			nowStatus.set("Waiting...");
		}
	}
	
	@FXML
	private void reviewOnClicked() {
		if (reviewCheckBox.isSelected()) {
			historyListView.setVisible(true);
			applyBtn.setVisible(true);
			getStepBtn.setDisable(true);
			captureBtn.setDisable(true);
			if (history.history.size() > 0) {
				historyListView.getSelectionModel().select(history.history.size() - 1);
				historyIsSelected();
			}
		} else {
			historyListView.setVisible(false);
			applyBtn.setVisible(false);
			getStepBtn.setDisable(false);
			captureBtn.setDisable(false);
			drawChessBoard(ChessBoard.board);
		}
	}
	
	@FXML
	private void applyOnClicked() {
		int index = historyListView.getSelectionModel().getSelectedIndex();
		try {
			ChessBoard.board = ChessBoard.cloneBoard(history.history.get(index).board);
			for (Chess[] chesses : ChessBoard.board) {
				for (Chess chess : chesses) {
					chess.update();
				}
			}
		} catch (CloneNotSupportedException e) {
			e.printStackTrace();
		}
		int legalCount = 0;
		for (int i = history.history.size() - 1; i > index; i--) {
			if (history.history.get(i).behavior.equals("legal")) legalCount++;
			history.removeStep(i);
		}
		stepCount.setValue(String.valueOf(Integer.valueOf(stepCount.getValue()) - legalCount));
	}
	
	private void historyIsSelected() {
		int index = historyListView.getSelectionModel().getSelectedIndex();
		Platform.runLater(() -> drawChessBoard(history.history.get(index).board));
	}
	
	public void initialize() {
		history = Main.history;

		//Make the console always scroll to the bottom
		this.console.textProperty().addListener(
				(ObservableValue<? extends String> observableValue, String oldValue, String newValue)
						-> this.console.setScrollTop(1.7976931348623157E308D));
		historyListView.setItems(history.historyTextList);
		historyListView.getSelectionModel().selectedItemProperty().addListener(
				(ObservableValue<? extends String> observable, String oldValue, String newValue)
						-> historyIsSelected());
		new ChessBoard();
		drawChessBoardBase();
		stepCountLabel.textProperty().bind(stepCount);
		actionLabel.textProperty().bind(nowStatus);
		
		if (Main.isAiMode) {
			console.appendText("Game is start. I'm " + (Main.isBlackPlayer ? "black player" : "white player") + "\n");
		} else {
			nowStatus.setValue("Replaying...");
			getStepBtn.setDisable(true);
			captureBtn.setDisable(true);
			console.setVisible(false);
			historyListView.setVisible(true);
			reviewCheckBox.setSelected(true);
			reviewCheckBox.setDisable(true);
			console.appendText("Replay mode.\n");
			if (history.history.size() > 0) {
				historyListView.getSelectionModel().select(history.history.size() - 1);
				Platform.runLater(() -> drawChessBoard(history.history.get(history.history.size() - 1).board));
			}
		}
	}
	
	
	
	private void drawChessBoardBase() {
		double
				margin = 50,            //chessboard line margin
				lineSpacing = 62.5,     //spacing between two lines
				textMargin = 10,        //text margin
				textOffset = 3;         //make text be on line center
		
		//Draw Rows
		for (int i = 0; i < 9; i++) {
			Line line = new Line(margin, margin + lineSpacing * i, margin + 500, margin + lineSpacing * i);
			Text text = new Text(textMargin, margin + lineSpacing * i + textOffset, String.valueOf(i + 1));
			chessboardUI.getChildren().addAll(line, text);
		}
		
		//Draw Columns
		for (int i = 0; i < 9; i++) {
			Line line = new Line(margin + lineSpacing * i, margin, margin + lineSpacing * i, margin + 500);
			Text text = new Text(margin + lineSpacing * i - textOffset, textMargin + 2 * textOffset, String.valueOf((char) (i + 'A')));
			chessboardUI.getChildren().addAll(line, text);
		}
		
		//Initialize Canvas
		Canvas chessCanvas = new Canvas();
		chessCanvas.setHeight(600);
		chessCanvas.setWidth(600);
		chessboardUI.getChildren().add(chessCanvas);
		mainGC = chessCanvas.getGraphicsContext2D();
		mainGC.setLineWidth(3);
		mainGC.setFont(Font.font("console", FontWeight.BOLD, 50));
		
		Canvas captureCanvas = new Canvas();
		captureCanvas.setHeight(600);
		captureCanvas.setWidth(600);
		chessboardUI.getChildren().add(captureCanvas);
		captureGC = captureCanvas.getGraphicsContext2D();
		captureGC.setLineWidth(3);
		captureGC.setStroke(Color.RED);
	}
	
	private void drawChessBoard(Chess[][] board) {
		mainGC.clearRect(0, 0, 600, 600);
		for (Chess[] chesses : board) {
			for (Chess chess : chesses) {
				drawChessShape(chess.coord, chess.status);
			}
		}
	}
	
	private void drawChessShape(Coord coord, char status) {
		double centerX = 20 + 62.5 * (coord.x);
		double centerY = 20 + 62.5 * (coord.y);
		boolean drawMark = false;
		
		mainGC.clearRect(centerX - 1, centerY - 1, 62.5, 62.5);
		switch (status) {
			case 'b':
				mainGC.setFill(Color.BLACK);
				break;
			case 'w':
				mainGC.setFill(Color.WHITE);
				break;
			case '?':
				drawMark = true;
				mainGC.setFill(Main.isBlackPlayer ? Color.WHITE : Color.BLACK);
				break;
			case 't':
				mainGC.setStroke(Color.rgb(102, 204, 255));
				mainGC.strokeRect(centerX, centerY, 60, 60);
				mainGC.setFill(Main.isBlackPlayer ? Color.BLACK : Color.WHITE);
				break;
			case 'e':
				return;
		}
		mainGC.fillRoundRect(centerX, centerY, 60, 60, 60, 60);
		if (drawMark) {
			mainGC.setFill(Color.RED);
			mainGC.fillText("?", centerX + 20, centerY + 47);
		}
	}
	
	private void drawChessBorder(Coord coord, GraphicsContext gc) {
		double centerX = 20 + 62.5 * coord.x, centerY = 20 + 62.5 * coord.y;
		gc.strokeRect(centerX, centerY, 60, 60);
		gc.clearRect(centerX - 1.5, centerY + 20, 3, 20);
		gc.clearRect(centerX + 20, centerY - 1.5, 20, 3);
		gc.clearRect(centerX + 60 - 1.5, centerY + 20, 3, 20);
		gc.clearRect(centerX + 20, centerY + 60 - 1.5, 20, 3);
	}
	
	private Coord mousePosToChessCoord(Coord mousePos) {
		return new Coord((int) ((mousePos.x - 20) / 62.5), (int) ((mousePos.y - 20) / 62.5));
	}
	
	
	private AtomicBoolean runningCapture = new AtomicBoolean(false);
	private volatile List<Coord> captureCoords = new ArrayList<>();
	
	private void runCapture() {
		new Thread(() -> {
			while (true) {
				if (runningCapture.get()) {
					captureGC.clearRect(0, 0, 600, 600);
					if (mousePos.x <= 600 && mousePosToChessCoord(mousePos).isLegal()) {
						Chess temp = ChessBoard.getChess(mousePosToChessCoord(mousePos));
						if (temp.group != null)
							temp.group.chesses.forEach(item -> drawChessBorder(item.coord, captureGC));
						else
							drawChessBorder(temp.coord, captureGC);
					}
				} else {
					Platform.runLater(() -> {
						console.appendText("--> Capture: ");
						captureCoords.forEach(item -> console.appendText(item.toString() + ", "));
						console.appendText("Finish! \n");
						captureCoords.clear();
					});
					return;
				}
				try {
					Thread.sleep(20);
				} catch (InterruptedException ie) {
					ie.printStackTrace();
				}
			}
		}).start();
	}
}
