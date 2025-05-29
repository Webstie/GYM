package GUI;

import Client.MeetingScheduler;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.net.InetAddress;
import java.util.*;
import java.util.function.Consumer;

public class GBMS_GUI extends Application {
    private TextField activityField;
    private DatePicker datePicker;
    private TextField timeField;
    private TextArea ipField;
    private Spinner<Integer> minParticipantsSpinner;
    private TextArea console;
    private MeetingScheduler scheduler;
    private static int bookingIdCounter = 100;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        primaryStage.setTitle("Gym Booking Management System");

        // 初始化 scheduler
        InetAddress serverIP = InetAddress.getByName("127.0.0.1");
        int clientPort = 9877;
        int serverPort = 9876;
        scheduler = new MeetingScheduler(clientPort, serverPort, serverIP);

        // 设置弹窗 handler
        scheduler.setPopupHandler((info, callback) -> showAcceptRejectPopup(info, callback));

        // 控制台日志回调
        scheduler.addMessageListener(msg -> Platform.runLater(() -> {
            if (msg == null) console.appendText("[null message]\n");
            else console.appendText(msg + "\n");
        }));
        scheduler.start();

        console = new TextArea();
        console.setEditable(false);
        console.setPrefHeight(200);

        TabPane tabPane = new TabPane();
        tabPane.getTabs().addAll(
                new Tab("Book Meeting", buildBookingTab()),
                new Tab("Cancel Meeting", buildCancelTab()),
                new Tab("Add Participant", buildAddTab()),
                new Tab("Withdraw", buildWithdrawTab())
        );
        tabPane.getTabs().forEach(tab -> tab.setClosable(false));

        VBox root = new VBox(tabPane, new Label("Console Log:"), console);
        root.setPadding(new Insets(10));
        root.setSpacing(10);

        primaryStage.setScene(new Scene(root, 550, 550));
        primaryStage.show();
    }

    private VBox buildBookingTab() {
        activityField = new TextField();
        timeField = new TextField();
        ipField = new TextArea();
        datePicker = new DatePicker();
        minParticipantsSpinner = new Spinner<>(1, 100, 2);

        Button sendButton = new Button("Send BOOK Request");
        sendButton.setOnAction(e -> sendBookingRequest());

        GridPane form = new GridPane();
        form.setPadding(new Insets(10));
        form.setVgap(8);
        form.setHgap(10);
        form.add(new Label("Activity Type:"), 0, 0);
        form.add(activityField, 1, 0);
        form.add(new Label("Date:"), 0, 1);
        form.add(datePicker, 1, 1);
        form.add(new Label("Time (HH-HH):"), 0, 2);
        form.add(timeField, 1, 2);
        form.add(new Label("Participant IPs (comma-separated):"), 0, 3);
        form.add(ipField, 1, 3);
        form.add(new Label("Minimum Participants:"), 0, 4);
        form.add(minParticipantsSpinner, 1, 4);
        form.add(sendButton, 1, 5);

        return new VBox(10, form);
    }

    private VBox buildCancelTab() {
        TextField meetingIdField = new TextField();
        Button cancelButton = new Button("Send CANCEL Request");
        cancelButton.setOnAction(e -> sendCancelRequest(meetingIdField.getText().trim()));
        return new VBox(10, new Label("Meeting ID to Cancel:"), meetingIdField, cancelButton);
    }

    private VBox buildAddTab() {
        TextField meetingIdField = new TextField();
        meetingIdField.setPromptText("e.g., MT#2");

        Button addButton = new Button("Send ADD Request");
        addButton.setOnAction(e -> {
            String meetingID = meetingIdField.getText().trim();
            if (meetingID.isEmpty()) {
                console.appendText("⚠️ Meeting ID cannot be empty\n");
                return;
            }

            new Thread(() -> {
                scheduler.sendAddRequest(meetingID);
                Platform.runLater(() -> {
                    console.appendText("📤 Sent ADD request for: " + meetingID + "\n");
                });
            }).start();
        });

        VBox addLayout = new VBox(10, new Label("Meeting ID to Add Participant:"), meetingIdField, addButton);
        addLayout.setPadding(new Insets(10));
        return addLayout;
    }

    private VBox buildWithdrawTab() {
        TextField meetingIdField = new TextField();
        meetingIdField.setPromptText("e.g., MT#2");

        Button withdrawButton = new Button("Send WITHDRAW Request");
        withdrawButton.setOnAction(e -> {
            String meetingID = meetingIdField.getText().trim();
            if (meetingID.isEmpty()) {
                console.appendText("⚠️ Meeting ID cannot be empty\n");
                return;
            }

            new Thread(() -> {
                scheduler.sendWithdrawRequest(meetingID);
                Platform.runLater(() -> {
                    console.appendText("📤 Sent WITHDRAW request for: " + meetingID + "\n");
                });
            }).start();
        });

        VBox withdrawLayout = new VBox(10, new Label("Meeting ID to Withdraw From:"), meetingIdField, withdrawButton);
        withdrawLayout.setPadding(new Insets(10));
        return withdrawLayout;
    }

    private void sendBookingRequest() {
        String activity = activityField.getText().trim();
        String date = datePicker.getValue() != null ? datePicker.getValue().toString() : "";
        String time = timeField.getText().trim();
        List<String> ips = Arrays.asList(ipField.getText().trim().split(","));
        int min = minParticipantsSpinner.getValue();

        if (activity.isEmpty() || date.isEmpty() || time.isEmpty() || ips.isEmpty()) {
            console.appendText("⚠️ Please fill out all fields\n");
            return;
        }
        String requestId = "RQ#" + (bookingIdCounter++);
        scheduler.sendBookRequest(requestId, date, time, activity, ips, min);
    }

    public void sendCancelRequest(String meetingId) {
        new Thread(() -> {
            scheduler.sendCancelRequest(meetingId);
            Platform.runLater(() -> console.appendText("📤 Sent CANCEL request for: " + meetingId + "\n"));
        }).start();
    }

    public void showAcceptRejectPopup(String info, Consumer<Boolean> callback) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Meeting Invitation");
            alert.setHeaderText("New Invite");
            alert.setContentText(info);
            Optional<ButtonType> result = alert.showAndWait();
            callback.accept(result.isPresent() && result.get() == ButtonType.OK);
        });
    }
}
