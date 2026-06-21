package com.clanevents;

import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class EventCardPanel extends JPanel
{
	private static final DateTimeFormatter TIME_FMT =
		DateTimeFormatter.ofPattern("EEE dd MMM, h:mm a z");
	private static final Color CARD_BG = new Color(0x2A2A2A);
	private static final Color RSVP_COLOR = new Color(0x1ABC9C);

	private final ClanEvent event;
	private boolean expanded = false;

	private final JPanel expandedSection;
	private final JButton rsvpBtn;

	EventCardPanel(ClanEvent event, String currentUsername, boolean canDelete,
		Consumer<ClanEvent> onRsvp, Consumer<ClanEvent> onDelete)
	{
		this.event = event;

		setLayout(new BorderLayout(0, 0));
		setBackground(CARD_BG);
		setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 0, 1, 0, ColorScheme.DARK_GRAY_COLOR),
			new EmptyBorder(6, 8, 6, 8)
		));

		// --- Collapsed header row ---
		JPanel header = new JPanel(new BorderLayout(4, 0));
		header.setBackground(CARD_BG);

		JPanel titleRow = new JPanel();
		titleRow.setLayout(new BoxLayout(titleRow, BoxLayout.Y_AXIS));
		titleRow.setBackground(CARD_BG);

		JLabel titleLabel = new JLabel(event.title);
		titleLabel.setForeground(Color.WHITE);
		titleLabel.setFont(FontManager.getRunescapeBoldFont());

		ZonedDateTime zdt = ZonedDateTime.ofInstant(
			Instant.ofEpochSecond(event.timestampUtc), ZoneId.systemDefault());
		JLabel timeLabel = new JLabel(TIME_FMT.format(zdt));
		timeLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		timeLabel.setFont(FontManager.getRunescapeSmallFont());

		JLabel hostLabel = new JLabel("Host: " + event.host);
		hostLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		hostLabel.setFont(FontManager.getRunescapeSmallFont());

		titleRow.add(titleLabel);
		titleRow.add(Box.createVerticalStrut(2));
		titleRow.add(timeLabel);
		titleRow.add(Box.createVerticalStrut(1));
		titleRow.add(hostLabel);

		// RSVP count badge
		JLabel countLabel = new JLabel(event.rsvps.size() + " going");
		countLabel.setForeground(RSVP_COLOR);
		countLabel.setFont(FontManager.getRunescapeSmallFont());
		countLabel.setHorizontalAlignment(SwingConstants.RIGHT);

		header.add(titleRow, BorderLayout.CENTER);
		header.add(countLabel, BorderLayout.EAST);

		// Make header clickable to expand
		header.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		header.addMouseListener(new java.awt.event.MouseAdapter()
		{
			@Override
			public void mouseClicked(java.awt.event.MouseEvent e)
			{
				toggleExpanded();
			}
		});

		// --- Expanded section ---
		expandedSection = new JPanel();
		expandedSection.setLayout(new BoxLayout(expandedSection, BoxLayout.Y_AXIS));
		expandedSection.setBackground(CARD_BG);
		expandedSection.setBorder(new EmptyBorder(6, 0, 0, 0));
		expandedSection.setVisible(false);

		// Attendees list
		JLabel attendeesTitle = new JLabel("Attending (" + event.rsvps.size() + "):");
		attendeesTitle.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		attendeesTitle.setFont(FontManager.getRunescapeSmallFont());
		expandedSection.add(attendeesTitle);
		expandedSection.add(Box.createVerticalStrut(2));

		if (event.rsvps.isEmpty())
		{
			JLabel noneLabel = new JLabel("No RSVPs yet");
			noneLabel.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
			noneLabel.setFont(FontManager.getRunescapeSmallFont());
			expandedSection.add(noneLabel);
		}
		else
		{
			for (String name : event.rsvps)
			{
				JLabel nameLabel = new JLabel("• " + name);
				nameLabel.setForeground(Color.WHITE);
				nameLabel.setFont(FontManager.getRunescapeSmallFont());
				expandedSection.add(nameLabel);
			}
		}

		expandedSection.add(Box.createVerticalStrut(6));

		// RSVP / Un-RSVP button
		boolean alreadyRsvpd = event.rsvps.contains(currentUsername);
		rsvpBtn = new JButton(alreadyRsvpd ? "Un-RSVP" : "RSVP");
		rsvpBtn.setBackground(alreadyRsvpd ? ColorScheme.MEDIUM_GRAY_COLOR : RSVP_COLOR);
		rsvpBtn.setForeground(Color.WHITE);
		rsvpBtn.setFont(FontManager.getRunescapeSmallFont());
		rsvpBtn.setBorderPainted(false);
		rsvpBtn.setFocusPainted(false);
		rsvpBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		rsvpBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
		rsvpBtn.addActionListener(e -> onRsvp.accept(event));
		expandedSection.add(rsvpBtn);

		// Delete button (only if permitted)
		if (canDelete)
		{
			expandedSection.add(Box.createVerticalStrut(4));
			JButton deleteBtn = new JButton("Delete Event");
			deleteBtn.setBackground(new Color(0xC0392B));
			deleteBtn.setForeground(Color.WHITE);
			deleteBtn.setFont(FontManager.getRunescapeSmallFont());
			deleteBtn.setBorderPainted(false);
			deleteBtn.setFocusPainted(false);
			deleteBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			deleteBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
			deleteBtn.addActionListener(e -> onDelete.accept(event));
			expandedSection.add(deleteBtn);
		}

		add(header, BorderLayout.NORTH);
		add(expandedSection, BorderLayout.CENTER);
	}

	private void toggleExpanded()
	{
		expanded = !expanded;
		expandedSection.setVisible(expanded);
		revalidate();
		repaint();

		// Notify parent scroll pane to adjust
		Container parent = getParent();
		if (parent != null)
		{
			parent.revalidate();
		}
	}
}
