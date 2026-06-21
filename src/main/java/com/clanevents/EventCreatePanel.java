package com.clanevents;

import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class EventCreatePanel extends JPanel
{
	private static final Map<String, String> TIMEZONES = new LinkedHashMap<>();

	static
	{
		TIMEZONES.put("Local (system)", null);
		TIMEZONES.put("UTC", "UTC");
		TIMEZONES.put("US Eastern (ET)", "America/New_York");
		TIMEZONES.put("US Central (CT)", "America/Chicago");
		TIMEZONES.put("US Mountain (MT)", "America/Denver");
		TIMEZONES.put("US Pacific (PT)", "America/Los_Angeles");
		TIMEZONES.put("Brazil (BRT)", "America/Sao_Paulo");
		TIMEZONES.put("UK (GMT/BST)", "Europe/London");
		TIMEZONES.put("Central Europe (CET)", "Europe/Paris");
		TIMEZONES.put("Eastern Europe (EET)", "Europe/Helsinki");
		TIMEZONES.put("India (IST)", "Asia/Kolkata");
		TIMEZONES.put("Singapore (SGT)", "Asia/Singapore");
		TIMEZONES.put("Australia East (AEST)", "Australia/Sydney");
	}

	private static final int[] REMINDER_OPTIONS = {15, 30, 60, 120, 1440};
	private static final String[] REMINDER_LABELS = {"15 min", "30 min", "1 hour", "2 hours", "24 hours"};

	private final JTextField titleField = new JTextField();
	private final JTextField hostField = new JTextField();
	private final JSpinner dateSpinner;
	private final JSpinner hourSpinner;
	private final JSpinner minuteSpinner;
	private final JComboBox<String> timezoneCombo;
	private final JCheckBox[] reminderBoxes = new JCheckBox[REMINDER_OPTIONS.length];
	private final JLabel statusLabel = new JLabel(" ");

	EventCreatePanel(String currentUsername, Consumer<ClanEvent> onSubmit, Runnable onCancel)
	{
		setLayout(new BorderLayout(0, 8));
		setBackground(ColorScheme.DARKER_GRAY_COLOR);
		setBorder(new EmptyBorder(8, 8, 8, 8));

		// Header
		JPanel header = new JPanel(new BorderLayout());
		header.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		JLabel title = new JLabel("Create Event");
		title.setForeground(Color.WHITE);
		title.setFont(FontManager.getRunescapeBoldFont());
		JButton backBtn = new JButton("← Back");
		backBtn.setFont(FontManager.getRunescapeSmallFont());
		backBtn.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		backBtn.setBorderPainted(false);
		backBtn.setContentAreaFilled(false);
		backBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		backBtn.addActionListener(e -> onCancel.run());
		header.add(title, BorderLayout.WEST);
		header.add(backBtn, BorderLayout.EAST);

		// Form
		JPanel form = new JPanel();
		form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
		form.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		// Title field
		form.add(makeLabel("Event Name"));
		form.add(Box.createVerticalStrut(2));
		styleTextField(titleField);
		titleField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
		form.add(titleField);
		form.add(Box.createVerticalStrut(6));

		// Host field
		form.add(makeLabel("Host"));
		form.add(Box.createVerticalStrut(2));
		styleTextField(hostField);
		hostField.setText(currentUsername != null ? currentUsername : "");
		hostField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
		form.add(hostField);
		form.add(Box.createVerticalStrut(6));

		// Date spinner
		form.add(makeLabel("Date"));
		form.add(Box.createVerticalStrut(2));
		SpinnerDateModel dateModel = new SpinnerDateModel(new Date(), null, null, Calendar.DAY_OF_MONTH);
		dateSpinner = new JSpinner(dateModel);
		JSpinner.DateEditor dateEditor = new JSpinner.DateEditor(dateSpinner, "yyyy-MM-dd");
		dateSpinner.setEditor(dateEditor);
		styleSpinner(dateSpinner);
		dateSpinner.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
		form.add(dateSpinner);
		form.add(Box.createVerticalStrut(6));

		// Time row (hour : minute)
		form.add(makeLabel("Time"));
		form.add(Box.createVerticalStrut(2));
		JPanel timeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		timeRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		hourSpinner = new JSpinner(new SpinnerNumberModel(20, 0, 23, 1));
		minuteSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 59, 15));
		styleSpinner(hourSpinner);
		styleSpinner(minuteSpinner);
		hourSpinner.setPreferredSize(new Dimension(48, 26));
		minuteSpinner.setPreferredSize(new Dimension(48, 26));
		JLabel colon = new JLabel(":");
		colon.setForeground(Color.WHITE);
		colon.setBorder(new EmptyBorder(0, 4, 0, 4));
		timeRow.add(hourSpinner);
		timeRow.add(colon);
		timeRow.add(minuteSpinner);
		form.add(timeRow);
		form.add(Box.createVerticalStrut(6));

		// Timezone combo
		form.add(makeLabel("Timezone"));
		form.add(Box.createVerticalStrut(2));
		timezoneCombo = new JComboBox<>(TIMEZONES.keySet().toArray(new String[0]));
		timezoneCombo.setBackground(ColorScheme.DARK_GRAY_COLOR);
		timezoneCombo.setForeground(Color.WHITE);
		timezoneCombo.setFont(FontManager.getRunescapeSmallFont());
		timezoneCombo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
		form.add(timezoneCombo);
		form.add(Box.createVerticalStrut(6));

		// Reminders
		form.add(makeLabel("Reminders"));
		form.add(Box.createVerticalStrut(2));
		JPanel reminderPanel = new JPanel(new GridLayout(0, 2, 2, 2));
		reminderPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		for (int i = 0; i < REMINDER_OPTIONS.length; i++)
		{
			reminderBoxes[i] = new JCheckBox(REMINDER_LABELS[i]);
			reminderBoxes[i].setBackground(ColorScheme.DARKER_GRAY_COLOR);
			reminderBoxes[i].setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			reminderBoxes[i].setFont(FontManager.getRunescapeSmallFont());
			reminderPanel.add(reminderBoxes[i]);
		}
		form.add(reminderPanel);
		form.add(Box.createVerticalStrut(8));

		// Status label
		statusLabel.setFont(FontManager.getRunescapeSmallFont());
		statusLabel.setForeground(Color.RED);
		statusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		form.add(statusLabel);
		form.add(Box.createVerticalStrut(4));

		// Submit button
		JButton createBtn = new JButton("Create Event");
		createBtn.setBackground(new Color(0x1ABC9C));
		createBtn.setForeground(Color.WHITE);
		createBtn.setFont(FontManager.getRunescapeBoldFont());
		createBtn.setBorderPainted(false);
		createBtn.setFocusPainted(false);
		createBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		createBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
		createBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
		createBtn.addActionListener(e -> submit(onSubmit));
		form.add(createBtn);

		JScrollPane scroll = new JScrollPane(form);
		scroll.setBorder(null);
		scroll.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		scroll.getViewport().setBackground(ColorScheme.DARKER_GRAY_COLOR);

		add(header, BorderLayout.NORTH);
		add(scroll, BorderLayout.CENTER);
	}

	private void submit(Consumer<ClanEvent> onSubmit)
	{
		String title = titleField.getText().trim();
		String host = hostField.getText().trim();

		if (title.isEmpty())
		{
			statusLabel.setText("Event name is required.");
			return;
		}
		if (host.isEmpty())
		{
			statusLabel.setText("Host is required.");
			return;
		}

		// Resolve selected timezone
		String tzKey = (String) timezoneCombo.getSelectedItem();
		String tzId = TIMEZONES.get(tzKey);
		ZoneId zone = tzId != null ? ZoneId.of(tzId) : ZoneId.systemDefault();

		// Build ZonedDateTime from form inputs
		Date selectedDate = ((SpinnerDateModel) dateSpinner.getModel()).getDate();
		Calendar cal = Calendar.getInstance();
		cal.setTime(selectedDate);
		int year = cal.get(Calendar.YEAR);
		int month = cal.get(Calendar.MONTH) + 1;
		int day = cal.get(Calendar.DAY_OF_MONTH);
		int hour = (int) hourSpinner.getValue();
		int minute = (int) minuteSpinner.getValue();

		ZonedDateTime zdt = ZonedDateTime.of(LocalDateTime.of(year, month, day, hour, minute), zone);
		long epochSeconds = zdt.toInstant().toEpochMilli() / 1000;

		if (epochSeconds <= Instant.now().getEpochSecond())
		{
			statusLabel.setText("Event must be in the future.");
			return;
		}

		List<Integer> selectedReminders = new ArrayList<>();
		for (int i = 0; i < REMINDER_OPTIONS.length; i++)
		{
			if (reminderBoxes[i].isSelected())
			{
				selectedReminders.add(REMINDER_OPTIONS[i]);
			}
		}

		ClanEvent event = new ClanEvent();
		event.id = java.util.UUID.randomUUID().toString();
		event.title = title;
		event.host = host;
		event.timestampUtc = epochSeconds;
		event.reminders = selectedReminders;
		event.createdAt = Instant.now().getEpochSecond();

		statusLabel.setText(" ");
		onSubmit.accept(event);
	}

	public void setStatus(String message, boolean isError)
	{
		SwingUtilities.invokeLater(() ->
		{
			statusLabel.setText(message);
			statusLabel.setForeground(isError ? Color.RED : new Color(0x1ABC9C));
		});
	}

	private static JLabel makeLabel(String text)
	{
		JLabel label = new JLabel(text);
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		return label;
	}

	private static void styleTextField(JTextField field)
	{
		field.setBackground(ColorScheme.DARK_GRAY_COLOR);
		field.setForeground(Color.WHITE);
		field.setCaretColor(Color.WHITE);
		field.setFont(FontManager.getRunescapeSmallFont());
		field.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR),
			new EmptyBorder(2, 4, 2, 4)
		));
	}

	private static void styleSpinner(JSpinner spinner)
	{
		spinner.setBackground(ColorScheme.DARK_GRAY_COLOR);
		spinner.setForeground(Color.WHITE);
		spinner.getEditor().getComponent(0).setBackground(ColorScheme.DARK_GRAY_COLOR);
		((JComponent) spinner.getEditor().getComponent(0)).setForeground(Color.WHITE);
	}
}
