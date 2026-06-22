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
import java.util.function.Consumer;

public class EventCardPanel extends JPanel
{
	private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("EEE dd MMM");
	private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("h:mm a z");
	private static final Color TEAL = new Color(0x1ABC9C);
	private static final Color DELETE_RED = new Color(0xC0392B);

	private final JPanel expandedSection;
	private boolean expanded = false;

	EventCardPanel(ClanEvent event, String currentUsername, boolean canDelete,
		Consumer<ClanEvent> onRsvp, Consumer<ClanEvent> onDelete)
	{
		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		// Top margin between cards — same pattern as PartyMemberBox
		setBorder(new EmptyBorder(5, 0, 0, 0));

		// Inner container — darker card background with internal padding
		JPanel container = new JPanel(new BorderLayout(0, 0));
		container.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		container.setBorder(new EmptyBorder(7, 7, 7, 7));

		// --- Header (always visible) ---
		JPanel header = new JPanel(new BorderLayout(4, 0));
		header.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		// Left: event info stacked vertically
		JPanel infoPanel = new JPanel();
		infoPanel.setLayout(new BoxLayout(infoPanel, BoxLayout.Y_AXIS));
		infoPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		JLabel titleLabel = new JLabel(event.title);
		titleLabel.setFont(FontManager.getRunescapeBoldFont());
		titleLabel.setForeground(Color.WHITE);
		titleLabel.putClientProperty("html.disable", Boolean.TRUE);

		ZonedDateTime zdt = ZonedDateTime.ofInstant(
			Instant.ofEpochSecond(event.timestampUtc), ZoneId.systemDefault());

		JLabel dateLabel = new JLabel(DATE_FMT.format(zdt));
		dateLabel.setFont(FontManager.getRunescapeSmallFont());
		dateLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

		JLabel timeLabel = new JLabel(TIME_FMT.format(zdt));
		timeLabel.setFont(FontManager.getRunescapeSmallFont());
		timeLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

		JLabel hostLabel = new JLabel("Host: " + event.host);
		hostLabel.setFont(FontManager.getRunescapeSmallFont());
		hostLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		hostLabel.putClientProperty("html.disable", Boolean.TRUE);

		infoPanel.add(titleLabel);
		infoPanel.add(Box.createVerticalStrut(2));
		infoPanel.add(dateLabel);
		infoPanel.add(timeLabel);
		infoPanel.add(Box.createVerticalStrut(2));
		infoPanel.add(hostLabel);

		// Right: RSVP count
		JLabel countLabel = new JLabel(event.rsvps.size() + " going");
		countLabel.setFont(FontManager.getRunescapeSmallFont());
		countLabel.setForeground(event.rsvps.isEmpty() ? ColorScheme.MEDIUM_GRAY_COLOR : TEAL);
		countLabel.setVerticalAlignment(SwingConstants.TOP);

		header.add(infoPanel, BorderLayout.CENTER);
		header.add(countLabel, BorderLayout.EAST);

		// Click header to expand/collapse
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
		expandedSection.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		expandedSection.setBorder(new EmptyBorder(6, 0, 0, 0));
		expandedSection.setVisible(false);

		// Thin separator line
		JSeparator sep = new JSeparator();
		sep.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
		sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
		expandedSection.add(sep);
		expandedSection.add(Box.createVerticalStrut(6));

		// Attendees
		JLabel attendeesTitle = new JLabel("Attending (" + event.rsvps.size() + ")");
		attendeesTitle.setFont(FontManager.getRunescapeSmallFont());
		attendeesTitle.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		expandedSection.add(attendeesTitle);
		expandedSection.add(Box.createVerticalStrut(3));

		if (event.rsvps.isEmpty())
		{
			JLabel noneLabel = new JLabel("No one yet — be the first!");
			noneLabel.setFont(FontManager.getRunescapeSmallFont());
			noneLabel.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
			expandedSection.add(noneLabel);
		}
		else
		{
			for (String name : event.rsvps)
			{
				JLabel nameLabel = new JLabel("• " + name);
				nameLabel.setFont(FontManager.getRunescapeSmallFont());
				nameLabel.setForeground(Color.WHITE);
				nameLabel.putClientProperty("html.disable", Boolean.TRUE);
				expandedSection.add(nameLabel);
			}
		}

		expandedSection.add(Box.createVerticalStrut(8));

		// RSVP button
		boolean alreadyRsvpd = currentUsername != null && event.rsvps.contains(currentUsername);
		JButton rsvpBtn = new JButton(alreadyRsvpd ? "Remove RSVP" : "RSVP");
		styleButton(rsvpBtn, alreadyRsvpd ? ColorScheme.MEDIUM_GRAY_COLOR : TEAL);
		rsvpBtn.addActionListener(e -> onRsvp.accept(event));
		expandedSection.add(rsvpBtn);

		// Delete button
		if (canDelete)
		{
			expandedSection.add(Box.createVerticalStrut(4));
			JButton deleteBtn = new JButton("Delete Event");
			styleButton(deleteBtn, DELETE_RED);
			deleteBtn.addActionListener(e -> onDelete.accept(event));
			expandedSection.add(deleteBtn);
		}

		container.add(header, BorderLayout.CENTER);
		container.add(expandedSection, BorderLayout.SOUTH);

		add(container, BorderLayout.CENTER);
	}

	private void toggleExpanded()
	{
		expanded = !expanded;
		expandedSection.setVisible(expanded);
		revalidate();
		Container parent = getParent();
		if (parent != null)
		{
			parent.revalidate();
		}
	}

	private static void styleButton(JButton btn, Color bg)
	{
		btn.setBackground(bg);
		btn.setForeground(Color.WHITE);
		btn.setFont(FontManager.getRunescapeSmallFont());
		btn.setBorderPainted(false);
		btn.setFocusPainted(false);
		btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		btn.setAlignmentX(Component.LEFT_ALIGNMENT);
		btn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
	}
}
