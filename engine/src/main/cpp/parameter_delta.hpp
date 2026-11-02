// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 proleeeeeeeeee
#pragma once
#include "core/json.hpp"
#include <stdexcept>
#include <string>

inline bool parameters_omit_fields(const std::string& before,const std::string& after) {
    if (before.empty() || before==after) return false;
    spk::Json previous,next;std::string error;
    if (!spk::Json::parse(before,previous,error) || !spk::Json::parse(after,next,error) ||
        !previous.is_object() || !next.is_object()) throw std::invalid_argument("Invalid render settings");
    for (const auto& field:previous.fields()) if (!next.has(field.first)) return true;
    return false;
}

// The upstream ABI invalidates stages by field presence, even for equal values.
// Compare complete caller-owned settings, then send only the changed fields.
inline std::string parameter_delta(const std::string& before, const std::string& after) {
    spk::Json previous, next; std::string error;
    if (!spk::Json::parse(after,next,error) || !next.is_object())
        throw std::invalid_argument("Invalid render settings");
    if (before.empty()) return after;
    if (!spk::Json::parse(before,previous,error) || !previous.is_object())
        throw std::invalid_argument("Invalid previous render settings");
    auto delta=spk::Json::object();
    for (const auto& field:next.fields())
        if (!previous.has(field.first) || previous.at(field.first).dump()!=field.second.dump())
            delta.set(field.first,field.second);
    // Settings have explicit defaults at the Kotlin boundary. Silently dropping
    // a field would retain its old native value and make a cache key misleading.
    for (const auto& field:previous.fields())
        if (!next.has(field.first)) throw std::invalid_argument("Render settings cannot omit a previous field");
    return delta.dump();
}
