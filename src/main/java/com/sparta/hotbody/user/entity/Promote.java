package com.sparta.hotbody.user.entity;

import com.sparta.hotbody.common.TimeStamp;
import com.sparta.hotbody.user.dto.PromoteTrainerRequestDto;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor
public class Promote extends TimeStamp {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "Promote_ID")
  private Long id;
  @ManyToOne
  private User user;
  private String introduce;
  @Column
  private Integer isPromoted;


  public Promote(PromoteTrainerRequestDto requestDto, User user){
    this.user = user;
    this.introduce = requestDto.getIntroduce();
  }

  public void isPromoted(Integer isPromoted) {
    this.isPromoted = isPromoted;
  }
}