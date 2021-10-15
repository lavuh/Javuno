package solar.rpg.javuno.client.views;

import org.jetbrains.annotations.NotNull;
import solar.rpg.javuno.client.controller.ConnectionController;
import solar.rpg.javuno.mvc.IView;
import solar.rpg.javuno.mvc.JMVC;

import javax.swing.*;
import java.awt.*;

public class ViewServerConnect implements IView {

    @NotNull
    private final JMVC<ViewServerConnect, ConnectionController> mvc;
    @NotNull
    private final JPanel rootPanel;
    private JTextField usernameTextField;
    private JPasswordField serverPasswordTextField;
    private JTextField serverIpTextField;
    private JTextField serverPortTextField;
    private JButton connectButton;
    private JButton cancelButton;

    public ViewServerConnect(@NotNull JMVC<ViewServerConnect, ConnectionController> mvc) {
        this.mvc = mvc;
        rootPanel = new JPanel();
        generateUI();
    }

    public void setFormEntryEnabled(boolean enabled) {
        usernameTextField.setEnabled(enabled);
        serverIpTextField.setEnabled(enabled);
        serverPortTextField.setEnabled(enabled);
        serverPasswordTextField.setEnabled(enabled);
        connectButton.setEnabled(enabled);
        connectButton.setText(enabled ? "Connect" : "Connecting");
        cancelButton.setEnabled(!enabled);
    }

    private void onConnectExecute() {
        if (!connectButton.isEnabled()) return;

        String errorMessage = "";

        String serverIp = serverIpTextField.getText();
        if (serverIpTextField.getText().length() == 0)
            errorMessage += "Please enter a server IP\n";

        String serverPortText = serverPortTextField.getText();
        int serverPort = -1;
        if (serverPortText.length() == 0)
            errorMessage += "Please enter a server port\n";
        else {
            try {
                serverPort = Integer.parseInt(serverPortText);
                if (serverPort < 1 || serverPort > 65535) throw new NumberFormatException();
            } catch (NumberFormatException e) {
                errorMessage += "Please enter a valid server port (1-65535)\n";
            }
        }

        String username = usernameTextField.getText();
        if (username.length() == 0)
            errorMessage += "Please enter a username";

        String serverPassword = serverPasswordTextField.getText();

        if (!errorMessage.equals("")) {
            showErrorDialog("Validation Error", errorMessage);
            return;
        }

        final int finalServerPort = serverPort;

        SwingUtilities.invokeLater(() -> {
            setFormEntryEnabled(false);
            getMVC().getController().tryConnect(serverIp, finalServerPort, username, serverPassword);
        });
    }

    private void onCancelExecute() {
        assert !connectButton.isEnabled() : "Connect button is not disabled";
        assert cancelButton.isEnabled() : "Cancel button is not enabled";

        SwingUtilities.invokeLater(() -> {
            setFormEntryEnabled(true);
            getMVC().getController().cancelPendingConnect();
        });
    }

    @Override
    public void generateUI() {
        JPanel loginDetailsPanel = new JPanel();
        loginDetailsPanel.setLayout(new BoxLayout(loginDetailsPanel, BoxLayout.Y_AXIS));
        loginDetailsPanel.setMaximumSize(new Dimension(300, 150));
        loginDetailsPanel.setPreferredSize(new Dimension(300, 150));

        JPanel usernamePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JLabel usernameLabel = new JLabel("Username:");
        usernameTextField = new JTextField(8);
        usernameTextField.setDocument(new JTextFieldLimit(10));
        usernameLabel.setLabelFor(usernameTextField);
        usernamePanel.add(usernameLabel);
        usernamePanel.add(usernameTextField);

        JPanel serverIpPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JLabel serverIpLabel = new JLabel("Server IP Address:");
        serverIpTextField = new JTextField(12);
        serverIpTextField.setDocument(new JTextFieldLimit(15));
        serverIpLabel.setLabelFor(serverIpTextField);
        serverIpPanel.add(serverIpLabel);
        serverIpPanel.add(serverIpTextField);

        JPanel serverPortPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JLabel serverPortLabel = new JLabel("Server Port:");
        serverPortTextField = new JTextField(4);
        serverPortTextField.setDocument(new JTextFieldLimit(5));
        serverPortLabel.setLabelFor(serverPortTextField);
        serverPortPanel.add(serverPortLabel);
        serverPortPanel.add(serverPortTextField);

        JPanel serverPasswordPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JLabel serverPasswordLabel = new JLabel("Server Password:");
        serverPasswordTextField = new JPasswordField(10);
        serverPasswordLabel.setLabelFor(serverPasswordTextField);
        serverPasswordPanel.add(serverPasswordLabel);
        serverPasswordPanel.add(serverPasswordTextField);

        JPanel connectPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        connectButton = new JButton("Connect");
        connectButton.addActionListener((e) -> onConnectExecute());
        usernameTextField.addActionListener((e) -> onConnectExecute());
        serverIpTextField.addActionListener((e) -> onConnectExecute());
        serverPortTextField.addActionListener((e) -> onConnectExecute());
        serverPasswordTextField.addActionListener((e) -> onConnectExecute());
        cancelButton = new JButton("Cancel");
        cancelButton.setEnabled(false);
        cancelButton.addActionListener((e) -> onCancelExecute());
        connectPanel.add(connectButton);
        connectPanel.add(cancelButton);

        loginDetailsPanel.add(usernamePanel);
        loginDetailsPanel.add(serverIpPanel);
        loginDetailsPanel.add(serverPortPanel);
        loginDetailsPanel.add(serverPasswordPanel);
        loginDetailsPanel.add(connectPanel);

        rootPanel.setLayout(new BoxLayout(rootPanel, BoxLayout.Y_AXIS));
        rootPanel.add(Box.createVerticalGlue());
        rootPanel.add(loginDetailsPanel);
        rootPanel.add(Box.createVerticalGlue());
        rootPanel.setBackground(Color.getColor("#ffdead"));
    }

    @Override
    public JPanel getPanel() {
        return rootPanel;
    }

    @NotNull
    @Override
    public JMVC<ViewServerConnect, ConnectionController> getMVC() {
        return mvc;
    }
}