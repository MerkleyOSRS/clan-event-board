package com.clanevents;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
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

@Slf4j
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

	private static final int[]   REMINDER_OPTIONS = {15, 30, 60, 120, 1440};
	private static final String[] REMINDER_LABELS  = {"15 min", "30 min", "1 hour", "2 hours", "24 hours"};

	private static final Color TEAL       = new Color(0x1ABC9C);
	private static final Color TEAL_HOVER = new Color(0x16A085);
	private static final Color FIELD_BG   = new Color(0x222222);
	private static final Color FIELD_BORDER = new Color(0x383838);
	private static final Color SECTION_LINE = new Color(0x333333);

	private final JTextField    titleField  = new JTextField();
	private final JTextField    hostField   = new JTextField();
	private final JSpinner      dateSpinner;
	private final JSpinner      hourSpinner;
	private final JSpinner      minuteSpinner;
	private final JComboBox<String> timezoneCombo;
	private final JCheckBox[]   reminderBoxes = new JCheckBox[REMINDER_OPTIONS.length];
	private final JLabel        statusLabel   = new JLabel(" ");

	EventCreatePanel(String currentUsername, Consumer<ClanEvent> onSubmit, Runnable onCancel)
	{
		setLayout(new BorderLayout(0, 0));
		setBackground(ColorScheme.DARKER_GRAY_COLOR);

		// ── Header ────────────────────────────────────────────────────────────
		JPanel header = new JPanel(new BorderLayout());
		header.setBackground(ColorScheme.DARK_GRAY_COLOR);
		header.setBorder(new EmptyBorder(10, 10, 10, 8));

		JLabel titleLbl = new JLabel("Create Event");
		titleLbl.setForeground(Color.WHITE);
		titleLbl.setFont(FontManager.getRunescapeBoldFont());

		JButton backBtn = new JButton("← Back");
		backBtn.setFont(FontManager.getRunescapeSmallFont());
		backBtn.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
		backBtn.setBorderPainted(false);
		backBtn.setContentAreaFilled(false);
		backBtn.setFocusPainted(false);
		backBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		backBtn.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseEntered(MouseEvent e) { backBtn.setForeground(ColorScheme.LIGHT_GRAY_COLOR); }

			@Override
			public void mouseExited(MouseEvent e)  { backBtn.setForeground(ColorScheme.MEDIUM_GRAY_COLOR); }
		});
		backBtn.addActionListener(e -> onCancel.run());

		// Thin separator matching the list header
		JSeparator sep = new JSeparator();
		sep.setForeground(new Color(0x2E2E2E));
		sep.setBackground(ColorScheme.DARK_GRAY_COLOR);

		JPanel headerWrap = new JPanel(new BorderLayout());
		headerWrap.setBackground(ColorScheme.DARK_GRAY_COLOR);
		header.add(titleLbl, BorderLayout.WEST);
		header.add(backBtn, BorderLayout.EAST);
		headerWrap.add(header, BorderLayout.CENTER);
		headerWrap.add(sep, BorderLayout.SOUTH);

		// ── Form ──────────────────────────────────────────────────────────────
		JPanel form = new JPanel();
		form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
		form.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		form.setBorder(new EmptyBorder(10, 10, 10, 10));

		// Event name
		form.add(makeFieldLabel("Event Name"));
		form.add(Box.createVerticalStrut(3));
		styleTextField(titleField);
		titleField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
		titleField.setAlignmentX(Component.LEFT_ALIGNMENT);
		form.add(titleField);
		form.add(Box.createVerticalStrut(8));

		// Host
		form.add(makeFieldLabel("Host"));
		form.add(Box.createVerticalStrut(3));
		styleTextField(hostField);
		hostField.setText(currentUsername != null ? currentUsername : "");
		hostField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
		hostField.setAlignmentX(Component.LEFT_ALIGNMENT);
		form.add(hostField);
		form.add(Box.createVerticalStrut(12));

		// ── Section: When ────────────────────────────────────────────────────
		form.add(makeSectionLabel("When"));
		form.add(Box.createVerticalStrut(6));

		// Date
		form.add(makeFieldLabel("Date"));
		form.add(Box.createVerticalStrut(3));
		SpinnerDateModel dateModel = new SpinnerDateModel(new Date(), null, null, Calendar.DAY_OF_MONTH);
		dateSpinner = new JSpinner(dateModel);
		JSpinner.DateEditor dateEditor = new JSpinner.DateEditor(dateSpinner, "yyyy-MM-dd");
		dateSpinner.setEditor(dateEditor);
		styleSpinner(dateSpinner);
		dateSpinner.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
		dateSpinner.setAlignmentX(Component.LEFT_ALIGNMENT);
		form.add(dateSpinner);
		form.add(Box.createVerticalStrut(6));

		// Time — hour and minute on one row
		form.add(makeFieldLabel("Time"));
		form.add(Box.createVerticalStrut(3));
		JPanel timeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		timeRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		timeRow.setAlignmentX(Component.LEFT_ALIGNMENT);
		hourSpinner = new JSpinner(new SpinnerNumberModel(20, 0, 23, 1));
		minuteSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 59, 15));
		styleSpinner(hourSpinner);
		styleSpinner(minuteSpinner);
		hourSpinner.setPreferredSize(new Dimension(52, 28));
		minuteSpinner.setPreferredSize(new Dimension(52, 28));
		JLabel colon = new JLabel(":");
		colon.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		colon.setBorder(new EmptyBorder(0, 6, 0, 6));
		timeRow.add(hourSpinner);
		timeRow.add(colon);
		timeRow.add(minuteSpinner);
		form.add(timeRow);
		form.add(Box.createVerticalStrut(6));

		// Timezone
		form.add(makeFieldLabel("Timezone"));
		form.add(Box.createVerticalStrut(3));
		timezoneCombo = new JComboBox<>(TIMEZONES.keySet().toArray(new String[0]));
		timezoneCombo.setBackground(FIELD_BG);
		timezoneCombo.setForeground(Color.WHITE);
		timezoneCombo.setFont(FontManager.getRunescapeSmallFont());
		timezoneCombo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
		timezoneCombo.setAlignmentX(Component.LEFT_ALIGNMENT);
		form.add(timezoneCombo);
		form.add(Box.createVerticalStrut(12));

		// ── Section: Reminders ────────────────────────────────────────────────
		form.add(makeSectionLabel("Reminders"));
		form.add(Box.createVerticalStrut(6));

		JPanel reminderGrid = new JPanel(new GridLayout(0, 2, 4, 4));
		reminderGrid.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		reminderGrid.setAlignmentX(Component.LEFT_ALIGNMENT);
		for (int i = 0; i < REMINDER_OPTIONS.length; i++)
		{
			reminderBoxes[i] = new JCheckBox(REMINDER_LABELS[i]);
			reminderBoxes[i].setBackground(ColorScheme.DARKER_GRAY_COLOR);
			reminderBoxes[i].setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			reminderBoxes[i].setFont(FontManager.getRunescapeSmallFont());
			reminderBoxes[i].setFocusPainted(false);
			reminderGrid.add(reminderBoxes[i]);
		}
		form.add(reminderGrid);
		form.add(Box.createVerticalStrut(14));

		// Validation message
		statusLabel.setFont(FontManager.getRunescapeSmallFont());
		statusLabel.setForeground(new Color(0xE74C3C));
		statusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		form.add(statusLabel);
		form.add(Box.createVerticalStrut(6));

		// Submit button — rounded, teal, full width, hover feedback
		JButton createBtn = makeSubmitButton("Create Event");
		createBtn.addActionListener(e -> submit(onSubmit));
		form.add(createBtn);

		JScrollPane scroll = new JScrollPane(form);
		scroll.setBorder(null);
		scroll.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		scroll.getViewport().setBackground(ColorScheme.DARKER_GRAY_COLOR);

		add(headerWrap, BorderLayout.NORTH);
		add(scroll, BorderLayout.CENTER);
	}

	private void submit(Consumer<ClanEvent> onSubmit)
	{
		String title = titleField.getText().trim();
		String host  = hostField.getText().trim();

		if (title.isEmpty())
		{
			statusLabel.setText("Event name is required.");
			return;
		}
		if (title.length() > 80)
		{
			statusLabel.setText("Event name must be 80 characters or less.");
			return;
		}
		if (host.isEmpty())
		{
			statusLabel.setText("Host is required.");
			return;
		}
		if (host.length() > 50)
		{
			statusLabel.setText("Host name must be 50 characters or less.");
			return;
		}

		String tzKey = (String) timezoneCombo.getSelectedItem();
		String tzId  = TIMEZONES.get(tzKey);
		ZoneId zone  = tzId != null ? ZoneId.of(tzId) : ZoneId.systemDefault();

		Date     selectedDate = ((SpinnerDateModel) dateSpinner.getModel()).getDate();
		Calendar cal          = Calendar.getInstance();
		cal.setTime(selectedDate);
		int year   = cal.get(Calendar.YEAR);
		int month  = cal.get(Calendar.MONTH) + 1;
		int day    = cal.get(Calendar.DAY_OF_MONTH);
		int hour   = ((Number) hourSpinner.getValue()).intValue();
		int minute = ((Number) minuteSpinner.getValue()).intValue();

		ZonedDateTime zdt          = ZonedDateTime.of(LocalDateTime.of(year, month, day, hour, minute), zone);
		long          epochSeconds = zdt.toInstant().toEpochMilli() / 1000;

		if (epochSeconds <= Instant.now().getEpochSecond())
		{
			statusLabel.setText("Event time must be in the future.");
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
		event.id           = java.util.UUID.randomUUID().toString();
		event.title        = title;
		event.host         = host;
		event.timestampUtc = epochSeconds;
		event.reminders    = selectedReminders;
		event.createdAt    = Instant.now().getEpochSecond();

		statusLabel.setText(" ");
		onSubmit.accept(event);
	}

	public void setStatus(String message, boolean isError)
	{
		SwingUtilities.invokeLater(() ->
		{
			statusLabel.setText(message);
			statusLabel.setForeground(isError ? new Color(0xE74C3C) : TEAL);
		});
	}

	// ── Factory helpers ───────────────────────────────────────────────────────

	// Section header — dimmer label with a horizontal rule to its right
	private static JPanel makeSectionLabel(String text)
	{
		JPanel row = new JPanel(new BorderLayout(8, 0));
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 16));

		JLabel lbl = new JLabel(text.toUpperCase());
		lbl.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 9));
		lbl.setForeground(new Color(0x7A7A7A));

		JSeparator sep = new JSeparator();
		sep.setForeground(SECTION_LINE);
		sep.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		row.add(lbl, BorderLayout.WEST);
		row.add(sep, BorderLayout.CENTER);
		return row;
	}

	private static JLabel makeFieldLabel(String text)
	{
		JLabel label = new JLabel(text);
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		return label;
	}

	private static void styleTextField(JTextField field)
	{
		field.setBackground(FIELD_BG);
		field.setForeground(Color.WHITE);
		field.setCaretColor(Color.WHITE);
		field.setFont(FontManager.getRunescapeSmallFont());
		field.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(FIELD_BORDER),
			new EmptyBorder(3, 6, 3, 6)
		));
	}

	private static void styleSpinner(JSpinner spinner)
	{
		spinner.setBackground(FIELD_BG);
		spinner.setForeground(Color.WHITE);
		spinner.setBorder(BorderFactory.createLineBorder(FIELD_BORDER));
		if (spinner.getEditor() instanceof JSpinner.DefaultEditor)
		{
			JTextField tf = ((JSpinner.DefaultEditor) spinner.getEditor()).getTextField();
			tf.setBackground(FIELD_BG);
			tf.setForeground(Color.WHITE);
			tf.setCaretColor(Color.WHITE);
			tf.setBorder(new EmptyBorder(3, 4, 3, 4));
		}
	}

	private static JButton makeSubmitButton(String label)
	{
		JButton btn = new JButton(label)
		{
			@Override
			protected void paintComponent(Graphics g)
			{
				Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g2.setColor(getBackground());
				g2.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);
				g2.dispose();
				super.paintComponent(g);
			}
		};
		btn.setBackground(TEAL);
		btn.setForeground(Color.WHITE);
		btn.setFont(FontManager.getRunescapeBoldFont());
		btn.setBorderPainted(false);
		btn.setContentAreaFilled(false);
		btn.setOpaque(false);
		btn.setFocusPainted(false);
		btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		btn.setAlignmentX(Component.LEFT_ALIGNMENT);
		btn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
		btn.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseEntered(MouseEvent e) { btn.setBackground(TEAL_HOVER); }

			@Override
			public void mouseExited(MouseEvent e)  { btn.setBackground(TEAL); }
		});
		return btn;
	}
}
