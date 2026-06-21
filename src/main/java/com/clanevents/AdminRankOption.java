package com.clanevents;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AdminRankOption
{
	ADMINISTRATOR(100),
	DEPUTY_OWNER(125),
	OWNER(126);

	private final int minRankValue;
}
